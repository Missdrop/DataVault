package cn.missdrop.datavault.runtime.backend.redis;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.redis.RedisConfig;
import io.lettuce.core.*;
import io.lettuce.core.resource.DefaultClientResources;

/** Uses native multiplexing rather than borrowing a connection for every command. */
public final class RedisBackend {
    /** Owns independent I/O resources and closes partially allocated clients on failure. */
    public RedisDatabaseHandle open(PluginId owner, RedisConfig config, Runnable released) {
        var resources = DefaultClientResources.builder()
                .ioThreadPoolSize(2).computationThreadPoolSize(2).build();
        RedisClient client = null;
        try {
            RedisURI uri = RedisURI.create(config.uri());
            uri.setTimeout(config.timeout());
            client = RedisClient.create(resources, uri);
            client.setOptions(ClientOptions.builder()
                    .requestQueueSize(config.execution().queueCapacity())
                    .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                    .timeoutOptions(TimeoutOptions.enabled(config.timeout())).build());
            var connection = client.connect();
            try {
                connection.sync().ping();
                return new RedisDatabaseHandle(owner, config, client, resources, connection, released);
            } catch (Throwable failure) {
                connection.close();
                throw failure;
            }
        } catch (Throwable failure) {
            if (client != null) {
                client.shutdown();
            }
            resources.shutdown().syncUninterruptibly();
            throw failure;
        }
    }
}
