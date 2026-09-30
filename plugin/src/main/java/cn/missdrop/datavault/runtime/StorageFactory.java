package cn.missdrop.datavault.runtime;

import cn.missdrop.datavault.api.*;
import cn.missdrop.datavault.api.config.*;
import cn.missdrop.datavault.api.config.mongodb.MongoConfig;
import cn.missdrop.datavault.api.config.redis.RedisConfig;
import cn.missdrop.datavault.runtime.backend.mongodb.MongoBackend;
import cn.missdrop.datavault.runtime.backend.redis.RedisBackend;
import cn.missdrop.datavault.runtime.connection.PoolFactory;

/** Routes resource creation; registration and admission remain independent concerns. */
final class StorageFactory {
    private final PoolFactory jdbc = new PoolFactory();

    Storage open(PluginId owner, StorageConfig<?> config, Runnable released) throws Exception {
        if (config instanceof MongoConfig) {
            return new MongoBackend().open(owner, (MongoConfig) config, released);
        }
        if (config instanceof RedisConfig) {
            return new RedisBackend().open(owner, (RedisConfig) config, released);
        }
        if (!(config instanceof DatabaseConfig)) {
            throw new IllegalArgumentException("Unsupported storage configuration");
        }
        var pool = jdbc.open(owner, (DatabaseConfig) config);
        try {
            return new JdbcDatabase(owner, (DatabaseConfig) config, pool, released);
        } catch (Throwable failure) {
            pool.close();
            throw failure;
        }
    }
}
