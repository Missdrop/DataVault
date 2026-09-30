package cn.missdrop.datavault.benchmark;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.redis.RedisConfig;
import cn.missdrop.datavault.api.redis.RedisOperation;
import cn.missdrop.datavault.api.redis.RedisStorage;
import cn.missdrop.datavault.integration.DockerDatabase;
import cn.missdrop.datavault.runtime.DefaultDataVault;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.resource.DefaultClientResources;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Native async baseline: neither path adds a worker handoff to Lettuce's multiplexed connection. */
public class RedisBenchmarkDockerTest {
    @Test public void redis() throws Exception {
        try (var server = new DockerDatabase("redis:7.4", 6379)) {
            var vault = new DefaultDataVault(4, 4, 1024);
            var resources = DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
            String uri = "redis://127.0.0.1:" + server.port();
            var client = RedisClient.create(resources, uri);
            try {
                var storage = server.await(() -> vault.register(PluginId.of("benchmark"), RedisConfig.of(uri)).toCompletableFuture().get(15, TimeUnit.SECONDS));
                try (var connection = client.connect()) {
                    var commands = connection.async();
                    commands.set("benchmark:key", "value").get(5, TimeUnit.SECONDS);
                    compare("get", 1500, commands, storage, api -> api.get("benchmark:key").thenApply(value -> {
                        org.junit.Assert.assertEquals("value", value);
                        return 1;
                    }));
                    compare("set", 1000, commands, storage, api -> api.set("benchmark:key", "value").thenApply(value -> {
                        org.junit.Assert.assertEquals("OK", value);
                        return 1;
                    }));
                    Map<String, String> values = new LinkedHashMap<>();
                    for (int i = 0; i < 100; i++) { values.put("benchmark:" + i, "value"); }
                    compare("mset-100", 200, commands, storage, api -> api.mset(values).thenApply(value -> {
                        org.junit.Assert.assertEquals("OK", value);
                        return 100;
                    }));
                    org.junit.Assert.assertEquals(101, (long) connection.sync().dbsize());
                }
            } finally {
                client.shutdown();
                resources.shutdown().syncUninterruptibly();
                vault.shutdown().toCompletableFuture().get(20, TimeUnit.SECONDS);
            }
        }
    }

    private void compare(String name, int iterations, RedisAsyncCommands<String, String> commands,
                         RedisStorage storage, RedisOperation<Integer> operation) throws Exception {
        var paths = new LinkedHashMap<String, Callable<Integer>>();
        paths.put("direct-async", () -> operation.execute(commands).toCompletableFuture().get(10, TimeUnit.SECONDS));
        paths.put("datavault", () -> storage.execute(operation).toCompletableFuture().get(10, TimeUnit.SECONDS));
        NativeComparison.compare("Redis", name, iterations, paths);
    }
}
