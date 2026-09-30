package cn.missdrop.datavault.runtime.backend.postgresql;

import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.api.config.postgresql.PostgresqlConfig;
import cn.missdrop.datavault.runtime.connection.DriverBackend;
import com.zaxxer.hikari.HikariConfig;

/** Reuses server-prepared SQL and pgJDBC's bounded per-connection statement cache. */
public final class PostgresqlBackend extends DriverBackend {
    @Override
    public void configure(DatabaseConfig config, HikariConfig pool) {
        configureNetwork(pool, (PostgresqlConfig) config, "org.postgresql.Driver");
        pool.addDataSourceProperty("connectTimeout", "3");
        pool.addDataSourceProperty("socketTimeout", "10");
        pool.addDataSourceProperty("prepareThreshold", "5");
        pool.addDataSourceProperty("preparedStatementCacheQueries", "256");
        pool.addDataSourceProperty("preparedStatementCacheSizeMiB", "5");
        // reWriteBatchedInserts is URL opt-in because returned update counts may change.
    }
}
