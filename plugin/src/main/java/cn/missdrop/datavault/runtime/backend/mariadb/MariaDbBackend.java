package cn.missdrop.datavault.runtime.backend.mariadb;

import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.api.config.mariadb.MariaDbConfig;
import cn.missdrop.datavault.runtime.connection.DriverBackend;
import com.zaxxer.hikari.HikariConfig;

/** Native MariaDB binary protocol and driver-managed prepared statement reuse. */
public final class MariaDbBackend extends DriverBackend {
    @Override
    public void configure(DatabaseConfig config, HikariConfig pool) {
        configureNetwork(pool, (MariaDbConfig) config, "org.mariadb.jdbc.Driver");
        pool.addDataSourceProperty("connectTimeout", "3000");
        pool.addDataSourceProperty("socketTimeout", "10000");
        pool.addDataSourceProperty("useServerPrepStmts", "true");
        pool.addDataSourceProperty("cachePrepStmts", "true");
        pool.addDataSourceProperty("prepStmtCacheSize", "256");
        // The driver selects its native bulk protocol for eligible batches.
        pool.addDataSourceProperty("useBulkStmts", "true");
    }
}
