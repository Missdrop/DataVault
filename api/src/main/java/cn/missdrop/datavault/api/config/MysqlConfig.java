package cn.missdrop.datavault.api.config;

import cn.missdrop.datavault.api.DatabaseType;
import java.util.Objects;

/**
 * Dedicated MySQL pool. Configure TLS and network timeouts in the JDBC URL.
 * Supply credentials separately and never log the URL or password.
 */
public final class MysqlConfig implements DatabaseConfig {
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final PoolOptions pool;
    private final ExecutionOptions execution;

    /**
     * Validates and stores immutable settings before resources are allocated.
     * @param jdbcUrl MySQL driver URL with explicit network settings
     * @param username database username
     * @param password sensitive database password
     * @param pool per-owner pool limits
     * @param execution per-owner worker and queue limits
     * @throws IllegalArgumentException if settings exceed their supported bounds
     */
    public MysqlConfig(String jdbcUrl, String username, String password,
                       PoolOptions pool, ExecutionOptions execution) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        if (!jdbcUrl.startsWith("jdbc:mysql://") || jdbcUrl.length() <= 13) {
            throw new IllegalArgumentException("Expected a jdbc:mysql:// URL");
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
     * Creates a MySQL configuration with conservative independent pool and worker limits.
     * @param jdbcUrl MySQL driver URL
     * @param username database username
     * @param password sensitive database password
     * @return validated immutable configuration
     */
    public static MysqlConfig of(String jdbcUrl, String username, String password) {
        return new MysqlConfig(jdbcUrl, username, password,
                PoolOptions.defaults(), new ExecutionOptions(2, 256));
    }

    /**
     * Returns driver URL containing optional TLS and network settings.
     * @return driver URL containing optional TLS and network settings
     */
    public String jdbcUrl() {
        return jdbcUrl;
    }
    /**
     * Returns database username.
     * @return database username
     */
    public String username() {
        return username;
    }
    /**
     * Returns sensitive password, intended only for connection setup.
     * @return sensitive password, intended only for connection setup
     */
    public String password() {
        return password;
    }
    /**
     * Returns per-owner connection pool limits.
     * @return per-owner connection pool limits
     */
    public PoolOptions pool() {
        return pool;
    }

    @Override
    public DatabaseType type() { return DatabaseType.MYSQL; }

    @Override
    /**
     * Returns execution.
     * @return execution
     */
    public ExecutionOptions execution() {
        return execution;
    }
}
