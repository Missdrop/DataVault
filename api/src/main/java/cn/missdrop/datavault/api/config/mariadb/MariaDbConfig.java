package cn.missdrop.datavault.api.config.mariadb;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.config.*;

/** Dedicated MARIADB JDBC pool. URL options override provider defaults. */
public final class MariaDbConfig extends NetworkJdbcConfig {
    /** Creates independently bounded pool and worker settings. */
    public MariaDbConfig(String url, String username, String password,
                 PoolOptions pool, ExecutionOptions execution) {
        super(DatabaseType.MARIADB, "jdbc:mariadb://", url, username, password, pool, execution);
    }

    /** @return conservative per-owner defaults */
    public static MariaDbConfig of(String url, String username, String password) {
        return new MariaDbConfig(url, username, password, PoolOptions.defaults(), new ExecutionOptions(2, 256));
    }
}
