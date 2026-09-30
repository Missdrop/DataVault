package cn.missdrop.datavault.runtime.backend.redis;

import cn.missdrop.datavault.api.*;
import cn.missdrop.datavault.api.config.redis.RedisConfig;
import cn.missdrop.datavault.api.redis.*;
import cn.missdrop.datavault.runtime.execution.AsyncAdmission;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.resource.ClientResources;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Retains one owner-specific connection; admission protects its native pipeline. */
public final class RedisDatabaseHandle implements RedisStorage {
    private final PluginId owner;
    private final StatefulRedisConnection<String, String> connection;
    private final AsyncAdmission admission;

    RedisDatabaseHandle(PluginId owner, RedisConfig config, RedisClient client,
                        ClientResources resources, StatefulRedisConnection<String, String> connection,
                        Runnable released) {
        this.owner = owner;
        this.connection = connection;
        this.admission = new AsyncAdmission(config.execution().queueCapacity(), () -> {
            try {
                connection.close();
            } finally {
                try {
                    client.shutdown();
                } finally {
                    try {
                        resources.shutdown().syncUninterruptibly();
                    } finally {
                        released.run();
                    }
                }
            }
        });
    }

    @Override
    public PluginId owner() { return owner; }

    @Override
    public DatabaseType type() { return DatabaseType.REDIS; }

    @Override
    public <T> CompletionStage<T> execute(RedisOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        return admission.submit(() -> operation.execute(connection.async()));
    }

    @Override
    public CompletionStage<Void> close() {
        return admission.close();
    }
}
