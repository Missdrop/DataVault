package cn.missdrop.datavault.api.config.postgresql;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.config.*;

/** Dedicated POSTGRESQL JDBC pool. URL options override provider defaults. */
public final class PostgresqlConfig extends NetworkJdbcConfig {
    /** Creates independently bounded pool and worker settings. */
    public PostgresqlConfig(String url, String username, String password,
                 PoolOptions pool, ExecutionOptions execution) {
        super(DatabaseType.POSTGRESQL, "jdbc:postgresql://", url, username, password, pool, execution);
    }

    /** @return conservative per-owner defaults */
    public static PostgresqlConfig of(String url, String username, String password) {
        return new PostgresqlConfig(url, username, password, PoolOptions.defaults(), new ExecutionOptions(2, 256));
    }
}
