package cn.missdrop.datavault.api.config.mongodb;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.mongodb.MongoStorage;
import cn.missdrop.datavault.api.config.*;
import java.util.Objects;

/** Per-owner MongoClient pool settings. Connection URIs may contain secrets; never log them. */
public final class MongoConfig implements StorageConfig<MongoStorage> {
    private final String uri;
    private final String database;
    private final PoolOptions pool;
    private final ExecutionOptions execution;

    /** Validates immutable settings; driver-specific URI validation happens when opening.
     * @param uri connection URI, which may contain credentials
     * @param database selected logical database name
     * @param pool per-owner connection limits
     * @param execution per-owner worker and queue limits
     */
    public MongoConfig(String uri, String database, PoolOptions pool, ExecutionOptions execution) {
        this.uri = Objects.requireNonNull(uri, "uri");
        if (!uri.startsWith("mongodb://") && !uri.startsWith("mongodb+srv://")) {
            throw new IllegalArgumentException("Expected MongoDB URI");
        }
        this.database = Objects.requireNonNull(database, "database");
        if (database.isBlank()) {
            throw new IllegalArgumentException("Database name must not be blank");
        }
        this.pool = Objects.requireNonNull(pool, "pool");
        this.execution = Objects.requireNonNull(execution, "execution");
        if (execution.workers() > pool.maximumSize()) {
            throw new IllegalArgumentException("Workers exceed native pool size");
        }
    }

    /**
     * Returns independently bounded native client configuration.
     * @return independently bounded native client configuration
     * @param uri connection URI, which may contain credentials
     * @param database selected logical database name
     */
    public static MongoConfig of(String uri, String database) {
        return new MongoConfig(uri, database, PoolOptions.defaults(), new ExecutionOptions(2, 256));
    }

    /**
     * Returns sensitive connection URI.
     * @return sensitive connection URI
     */
    public String uri() {
        return uri;
    }

    /**
     * Returns selected logical database.
     * @return selected logical database
     */
    public String database() {
        return database;
    }

    /**
     * Returns pool limits per MongoDB server; monitoring sockets are additional.
     * @return pool limits per MongoDB server; monitoring sockets are additional
     */
    public PoolOptions pool() {
        return pool;
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.MONGODB;
    }

    @Override
    public ExecutionOptions execution() {
        return execution;
    }

    @Override
    public int connectionBudget() {
        return pool.maximumSize();
    }

    @Override
    public Class<MongoStorage> handleType() {
        return MongoStorage.class;
    }
}
