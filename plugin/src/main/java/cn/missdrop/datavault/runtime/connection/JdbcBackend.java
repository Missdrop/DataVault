package cn.missdrop.datavault.runtime.connection;

import cn.missdrop.datavault.api.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;

/** Driver-specific initialization; pool ownership and validation belong to PoolFactory. */
public interface JdbcBackend {
    /** Configures a fresh pool without opening connections. */
    void configure(DatabaseConfig config, HikariConfig pool) throws Exception;
}
