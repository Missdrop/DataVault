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

    public static MysqlConfig of(String jdbcUrl, String username, String password) {
        return new MysqlConfig(jdbcUrl, username, password,
                PoolOptions.defaults(), new ExecutionOptions(2, 256));
    }

    public String jdbcUrl() { return jdbcUrl; }
    public String username() { return username; }
    /** Sensitive value intended only for the connection provider. */
    public String password() { return password; }
    public PoolOptions pool() { return pool; }

    @Override
    public DatabaseType type() { return DatabaseType.MYSQL; }

    @Override
    public ExecutionOptions execution() { return execution; }
}
