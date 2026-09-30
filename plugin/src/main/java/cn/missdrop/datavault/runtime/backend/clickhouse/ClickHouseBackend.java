package cn.missdrop.datavault.runtime.backend.clickhouse;

import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.api.config.clickhouse.ClickHouseConfig;
import cn.missdrop.datavault.runtime.connection.DriverBackend;
import com.zaxxer.hikari.HikariConfig;

/** Native HTTP connection reuse; inserts use batches rather than fake JDBC transactions. */
public final class ClickHouseBackend extends DriverBackend {
    @Override
    public void configure(DatabaseConfig config, HikariConfig pool) {
        configureNetwork(pool, (ClickHouseConfig) config, "com.clickhouse.jdbc.ClickHouseDriver");
        pool.addDataSourceProperty("connection_timeout", "3000");
        pool.addDataSourceProperty("socket_timeout", "10000");
        // Keep driver compression enabled and preserve server durability/acknowledgment settings.
        // Async insert and experimental RowBinary optimizations remain explicit URL opt-ins.
    }
}
