package cn.missdrop.datavault.runtime.connection;

import cn.missdrop.datavault.api.config.NetworkJdbcConfig;
import com.zaxxer.hikari.HikariConfig;

/** Shared network-driver setup. Concrete backends supply only their own defaults. */
public abstract class DriverBackend implements JdbcBackend {
    protected void configureNetwork(HikariConfig pool, NetworkJdbcConfig config, String driver) {
        pool.setDriverClassName(driver);
        pool.setJdbcUrl(config.jdbcUrl());
        pool.setUsername(config.username());
        pool.setPassword(config.password());
    }
}
