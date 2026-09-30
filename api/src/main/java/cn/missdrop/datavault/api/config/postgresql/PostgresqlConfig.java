package cn.missdrop.datavault.api.config.postgresql;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.config.*;

/** Dedicated POSTGRESQL JDBC pool. URL options override provider defaults. */
public final class PostgresqlConfig extends NetworkJdbcConfig {
    /** Creates independently bounded pool and worker settings.
     * @param url JDBC endpoint and optional driver settings
     * @param username database login name
     * @param password sensitive database password
     * @param pool per-owner connection limits
     * @param execution per-owner worker and queue limits
     */
    public PostgresqlConfig(String url, String username, String password,
                 PoolOptions pool, ExecutionOptions execution) {
        super(DatabaseType.POSTGRESQL, "jdbc:postgresql://", url, username, password, pool, execution);
    }

    /**
     * Returns conservative per-owner defaults.
     * @return conservative per-owner defaults
     * @param url JDBC endpoint and optional driver settings
     * @param username database login name
     * @param password sensitive database password
     */
    public static PostgresqlConfig of(String url, String username, String password) {
        return new PostgresqlConfig(url, username, password, PoolOptions.defaults(), new ExecutionOptions(2, 256));
    }
}
