package cn.missdrop.datavault.integration;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.mongodb.MongoConfig;
import cn.missdrop.datavault.api.config.redis.RedisConfig;
import cn.missdrop.datavault.runtime.DefaultDataVault;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native driver tests preserve BSON and asynchronous command results without JDBC emulation. */
public class NativeDockerTest {
    @Test public void mongoDb() throws Exception {
        try (var server = new DockerDatabase("mongo:7", 27017)) {
            var vault = new DefaultDataVault(12, 8, 2048);
            var unblock = new CountDownLatch(1);
            try {
                var config = MongoConfig.of("mongodb://127.0.0.1:" + server.port(), "datavault_test");
                var first = server.await(() -> vault.register(PluginId.of("first"), config).toCompletableFuture().get(15, TimeUnit.SECONDS));
                var second = vault.register(PluginId.of("second"), config).toCompletableFuture().get(15, TimeUnit.SECONDS);
                var entered = new CountDownLatch(2);
                // Occupy both workers of one owner; another owner's workers must remain usable.
                var blockedA = first.execute(database -> { entered.countDown(); unblock.await(); return null; });
                var blockedB = first.execute(database -> { entered.countDown(); unblock.await(); return null; });
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                long count = second.execute(database -> {
                    var collection = database.getCollection("records");
                    collection.insertMany(List.of(new Document("value", 1), new Document("value", 2)));
                    collection.updateOne(new Document("value", 1), new Document("$set", new Document("value", 3)));
                    assertEquals(1, collection.countDocuments(new Document("value", 3)));
                    return collection.countDocuments();
                }).toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertEquals(2, count);
                unblock.countDown();
                blockedA.toCompletableFuture().get(5, TimeUnit.SECONDS);
                blockedB.toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertFalse(vault.find(PluginId.of("first")).isPresent());
                assertTrue(vault.findStorage(PluginId.of("first")).isPresent());
            } finally {
                unblock.countDown();
                vault.shutdown().toCompletableFuture().get(20, TimeUnit.SECONDS);
            }
        }
    }

    @Test public void redis() throws Exception {
        try (var server = new DockerDatabase("redis:7.4", 6379)) {
            var vault = new DefaultDataVault(4, 4, 2048);
            try {
                var config = RedisConfig.of("redis://127.0.0.1:" + server.port());
                var first = server.await(() -> vault.register(PluginId.of("first"), config).toCompletableFuture().get(15, TimeUnit.SECONDS));
                var second = vault.register(PluginId.of("second"), config).toCompletableFuture().get(15, TimeUnit.SECONDS);
                assertEquals("OK", first.execute(commands -> commands.set("first:key", "one")).toCompletableFuture().get(5, TimeUnit.SECONDS));
                assertEquals("one", second.execute(commands -> commands.get("first:key")).toCompletableFuture().get(5, TimeUnit.SECONDS));
                // Close one native connection; the second connection and event loops remain alive.
                first.close().toCompletableFuture().get(15, TimeUnit.SECONDS);
                assertFalse(vault.findStorage(PluginId.of("first")).isPresent());
                assertEquals("OK", second.execute(commands -> commands.set("second:key", "two")).toCompletableFuture().get(5, TimeUnit.SECONDS));
                assertEquals("two", second.execute(commands -> commands.get("second:key")).toCompletableFuture().get(5, TimeUnit.SECONDS));
            } finally {
                vault.shutdown().toCompletableFuture().get(20, TimeUnit.SECONDS);
            }
        }
    }
}
