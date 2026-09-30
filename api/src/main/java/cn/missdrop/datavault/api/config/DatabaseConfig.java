package cn.missdrop.datavault.api.config;

import cn.missdrop.datavault.api.DatabaseType;

/** Immutable backend configuration. Never log credentials. */
public interface DatabaseConfig {
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
}
