package cn.missdrop.datavault.api.config;

import cn.missdrop.datavault.api.DatabaseType;
import java.util.Objects;

/** Common JDBC network settings; concrete types validate their own URL scheme. */
public abstract class NetworkJdbcConfig implements DatabaseConfig {
    private final DatabaseType type;
    private final String url;
    private final String username;
    private final String password;
    private final PoolOptions pool;
    private final ExecutionOptions execution;

    /** Validates before any network I/O; credentials never appear in toString().
     * @param type concrete backend identity
     * @param prefix accepted JDBC URL prefix
     * @param url JDBC endpoint and optional driver settings
     * @param username database login name
     * @param password sensitive database password
     * @param pool per-owner connection limits
     * @param execution per-owner worker and queue limits
     */
    protected NetworkJdbcConfig(DatabaseType type, String prefix, String url,
                                String username, String password, PoolOptions pool,
                                ExecutionOptions execution) {
        this.type = Objects.requireNonNull(type, "type");
        this.url = Objects.requireNonNull(url, "url");
        if (!url.startsWith(prefix) || url.length() <= prefix.length()) {
            throw new IllegalArgumentException("Invalid JDBC URL scheme for " + type);
        }
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.pool = Objects.requireNonNull(pool, "pool");
        this.execution = Objects.requireNonNull(execution, "execution");
        if (execution.workers() > pool.maximumSize()) {
            throw new IllegalArgumentException("Worker count must not exceed pool size");
        }
    }

    /**
     * Returns JDBC endpoint and optional driver settings.
     * @return JDBC endpoint and optional driver settings
     */
    public final String jdbcUrl() {
        return url;
    }

    /**
     * Returns database username.
     * @return database username
     */
    public final String username() {
        return username;
    }

    /**
     * Returns sensitive password for connection initialization only.
     * @return sensitive password for connection initialization only
     */
    public final String password() {
        return password;
    }

    @Override
    public final PoolOptions pool() {
        return pool;
    }

    @Override
    public final DatabaseType type() {
        return type;
    }

    @Override
    public final ExecutionOptions execution() {
        return execution;
    }
}
