package cn.missdrop.datavault.runtime.connection;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.*;
import cn.missdrop.datavault.runtime.backend.sqlite.SqliteBackend;
import cn.missdrop.datavault.runtime.backend.mysql.MysqlBackend;
import cn.missdrop.datavault.runtime.backend.mariadb.MariaDbBackend;
import cn.missdrop.datavault.runtime.backend.postgresql.PostgresqlBackend;
import cn.missdrop.datavault.runtime.backend.h2.H2Backend;
import cn.missdrop.datavault.runtime.backend.duckdb.DuckDbBackend;
import cn.missdrop.datavault.runtime.backend.clickhouse.ClickHouseBackend;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.EnumMap;
import java.util.Map;

/** Validates and owns pools; backend implementations own driver-specific tuning. */
public final class PoolFactory {
    private final Map<DatabaseType, JdbcBackend> backends = new EnumMap<>(DatabaseType.class);

    /** Explicit backend setup is lazy; JDBC SPI can also discover providers on the classpath. */
    public PoolFactory() {
        backends.put(DatabaseType.SQLITE, new SqliteBackend());
        backends.put(DatabaseType.MYSQL, new MysqlBackend());
        backends.put(DatabaseType.MARIADB, new MariaDbBackend());
        backends.put(DatabaseType.POSTGRESQL, new PostgresqlBackend());
        backends.put(DatabaseType.H2, new H2Backend());
        backends.put(DatabaseType.DUCKDB, new DuckDbBackend());
        backends.put(DatabaseType.CLICKHOUSE, new ClickHouseBackend());
    }

    /** Opens and validates one pool; failed validation never leaks it. */
    public HikariDataSource open(PluginId owner, DatabaseConfig config) throws Exception {
        JdbcBackend backend = backends.get(config.type());
        if (backend == null) {
            throw new IllegalArgumentException("Backend is not JDBC: " + config.type());
        }
        HikariConfig pool = new HikariConfig();
        pool.setPoolName("DataVault-" + owner.value());
        pool.setMaximumPoolSize(config.pool().maximumSize());
        pool.setMinimumIdle(config.pool().minimumIdle());
        pool.setConnectionTimeout(config.pool().acquisitionTimeout().toMillis());
        if (config instanceof FileDatabaseConfig) {
            Files.createDirectories(((FileDatabaseConfig) config).file().getParent());
            // Retain embedded state/cache instead of reopening the native engine on idle or lifetime expiry.
            pool.setMaxLifetime(0);
            pool.setIdleTimeout(0);
        }
        backend.configure(config, pool);
        HikariDataSource source = new HikariDataSource(pool);
        try (Connection connection = source.getConnection()) {
            if (!connection.isValid(3)) {
                throw new IllegalStateException("Database validation failed");
            }
            return source;
        } catch (Throwable failure) {
            source.close();
            throw failure;
        }
    }
}
