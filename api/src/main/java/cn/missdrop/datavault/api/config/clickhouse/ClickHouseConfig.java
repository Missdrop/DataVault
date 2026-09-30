package cn.missdrop.datavault.api.config.clickhouse;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.config.*;

/** Dedicated CLICKHOUSE JDBC pool. URL options override provider defaults. */
public final class ClickHouseConfig extends NetworkJdbcConfig {
    /** Creates independently bounded pool and worker settings. */
    public ClickHouseConfig(String url, String username, String password,
                 PoolOptions pool, ExecutionOptions execution) {
        super(DatabaseType.CLICKHOUSE, "jdbc:clickhouse:", url, username, password, pool, execution);
    }

    /** @return conservative per-owner defaults */
    public static ClickHouseConfig of(String url, String username, String password) {
        return new ClickHouseConfig(url, username, password, PoolOptions.defaults(), new ExecutionOptions(2, 256));
    }

    @Override
    public boolean supportsTransactions() {
        return false;
    }
}
