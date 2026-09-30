package cn.missdrop.datavault.api.config;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.Database;

/** Immutable backend configuration. Never log credentials. */
public interface DatabaseConfig extends StorageConfig<Database> {
    /**
     * Returns configured JDBC backend.
     * @return configured JDBC backend
     */
    DatabaseType type();
    /**
     * Returns per-owner worker and bounded-queue settings.
     * @return per-owner worker and bounded-queue settings
     */
    ExecutionOptions execution();

    /**
     * Returns per-owner pool limits; embedded stores retain their single connection.
     * @return per-owner pool limits; embedded stores retain their single connection
     */
    default PoolOptions pool() {
        return new PoolOptions(1, 1, java.time.Duration.ofSeconds(3));
    }

    /**
     * Returns whether the backend supports ordinary JDBC commit and rollback.
     * @return whether the backend supports ordinary JDBC commit and rollback
     */
    default boolean supportsTransactions() {
        return true;
    }

    @Override
    default int connectionBudget() {
        return pool().maximumSize();
    }

    @Override
    default Class<Database> handleType() {
        return Database.class;
    }
}
