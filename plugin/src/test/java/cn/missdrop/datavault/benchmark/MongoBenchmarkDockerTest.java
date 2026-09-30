package cn.missdrop.datavault.benchmark;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.*;
import cn.missdrop.datavault.api.config.mongodb.MongoConfig;
import cn.missdrop.datavault.api.mongodb.MongoOperation;
import cn.missdrop.datavault.api.mongodb.MongoStorage;
import cn.missdrop.datavault.integration.DockerDatabase;
import cn.missdrop.datavault.runtime.DefaultDataVault;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.*;
import org.bson.Document;
import org.junit.Test;

/** Equivalent one-worker native pools; BSON callbacks are identical across all paths. */
public class MongoBenchmarkDockerTest {
    @Test public void mongoDb() throws Exception {
        try (var server = new DockerDatabase("mongo:7", 27017)) {
            var vault = new DefaultDataVault(4, 4, 1024);
            var worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(256), new ThreadPoolExecutor.AbortPolicy());
            String uri = "mongodb://127.0.0.1:" + server.port();
            var settings = MongoClientSettings.builder().applyConnectionString(new ConnectionString(uri))
                    .applyToConnectionPoolSettings(builder -> builder.maxSize(1).minSize(1).maxWaitTime(3, TimeUnit.SECONDS)).build();
            try (var client = MongoClients.create(settings)) {
                var config = new MongoConfig(uri, "datavault_test", new PoolOptions(1, 1, Duration.ofSeconds(3)), new ExecutionOptions(1, 256));
                var storage = server.await(() -> vault.register(PluginId.of("benchmark"), config).toCompletableFuture().get(15, TimeUnit.SECONDS));
                var database = client.getDatabase("datavault_test");
                database.getCollection("accounts").insertOne(new Document("_id", 1).append("balance", 0));
                compare("indexed-read", 500, database, storage, worker, db -> {
                    var row = db.getCollection("accounts").find(new Document("_id", 1)).first();
                    if (row == null) { throw new AssertionError("Missing row"); }
                    return row.getInteger("balance");
                });
                compare("single-update", 300, database, storage, worker, db -> (int) db.getCollection("accounts")
                        .updateOne(new Document("_id", 1), new Document("$inc", new Document("balance", 1))).getModifiedCount());
                compare("insert-many-20", 40, database, storage, worker, db -> {
                    var documents = new ArrayList<Document>();
                    for (int i = 0; i < 20; i++) { documents.add(new Document("value", i)); }
                    db.getCollection("events").insertMany(documents);
                    return documents.size();
                });
                org.junit.Assert.assertEquals(40 * 20 * 8 * 3, database.getCollection("events").countDocuments());
                org.junit.Assert.assertEquals(300 * 8 * 3, (int) database.getCollection("accounts").find(new Document("_id", 1)).first().getInteger("balance"));
            } finally {
                worker.shutdown();
                worker.awaitTermination(15, TimeUnit.SECONDS);
                vault.shutdown().toCompletableFuture().get(20, TimeUnit.SECONDS);
            }
        }
    }

    private void compare(String name, int iterations, MongoDatabase database, MongoStorage storage,
                         ExecutorService worker, MongoOperation<Integer> operation) throws Exception {
        var paths = new LinkedHashMap<String, Callable<Integer>>();
        paths.put("direct-sync", () -> operation.execute(database));
        paths.put("direct-async", () -> {
            var result = new CompletableFuture<Integer>();
            worker.execute(() -> {
                try { result.complete(operation.execute(database)); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
            return result.get(15, TimeUnit.SECONDS);
        });
        paths.put("datavault", () -> storage.execute(operation).toCompletableFuture().get(15, TimeUnit.SECONDS));
        NativeComparison.compare("MongoDB", name, iterations, paths);
    }
}
