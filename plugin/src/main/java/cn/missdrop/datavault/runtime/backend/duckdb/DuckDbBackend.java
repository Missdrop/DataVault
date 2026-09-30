package cn.missdrop.datavault.runtime.backend.duckdb;

import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.api.config.duckdb.DuckDbConfig;
import cn.missdrop.datavault.runtime.connection.JdbcBackend;
import com.zaxxer.hikari.HikariConfig;

/** Keeps the analytical engine warm while limiting each file's native resources. */
public final class DuckDbBackend implements JdbcBackend {
    @Override
    public void configure(DatabaseConfig config, HikariConfig pool) {
        DuckDbConfig duck = (DuckDbConfig) config;
        pool.setDriverClassName("org.duckdb.DuckDBDriver");
        pool.setJdbcUrl("jdbc:duckdb:" + duck.file());
        pool.addDataSourceProperty("threads", Integer.toString(duck.nativeThreads()));
        pool.addDataSourceProperty("memory_limit", duck.memoryLimitMb() + "MB");
        // Bulk import can unwrap DuckDBConnection and use Appender inside one callback.
    }
}
