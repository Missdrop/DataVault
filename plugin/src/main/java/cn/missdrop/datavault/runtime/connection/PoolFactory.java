package cn.missdrop.datavault.runtime.connection;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.*;
import com.mysql.cj.jdbc.MysqlDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.sql.Connection;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** Builds and validates one owner's pool. Driver-specific settings stay here. */
public final class PoolFactory {
    /** Validates the first connection before returning ownership to the caller. */
    public HikariDataSource open(PluginId owner, DatabaseConfig config) throws Exception {
        HikariConfig pool = new HikariConfig();
        pool.setPoolName("DataVault-" + owner.value());
        pool.setMinimumIdle(0);
        if (config instanceof SqliteConfig) {
            configureSqlite(pool, (SqliteConfig) config);
        } else if (config instanceof MysqlConfig) {
            configureMysql(pool, (MysqlConfig) config);
        } else {
            throw new IllegalArgumentException("Unsupported database configuration");
        }
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

    /** A single connection and worker serialize per-file access; WAL preserves external readers. */
    private void configureSqlite(HikariConfig pool, SqliteConfig config) throws Exception {
        Files.createDirectories(config.file().getParent());
        SQLiteConfig settings = new SQLiteConfig();
        settings.setBusyTimeout((int) config.busyTimeout().toMillis());
        settings.enforceForeignKeys(true);
        settings.setJournalMode(config.wal()
                ? SQLiteConfig.JournalMode.WAL : SQLiteConfig.JournalMode.DELETE);
        SQLiteDataSource source = new SQLiteDataSource(settings);
        source.setUrl("jdbc:sqlite:" + config.file());
        pool.setDataSource(source);
        pool.setMaximumPoolSize(1);
        pool.setConnectionTimeout(3000);
    }

    /** Supplies a driver data source directly, avoiding dependence on global driver discovery. */
    private void configureMysql(HikariConfig pool, MysqlConfig config) throws Exception {
        MysqlDataSource source = new MysqlDataSource();
        source.setUrl(config.jdbcUrl());
        // Driver settings can be overridden explicitly in the URL.
        source.setConnectTimeout(3000);
        source.setSocketTimeout(10000);
        pool.setDataSource(source);
        pool.setUsername(config.username());
        pool.setPassword(config.password());
        pool.setMaximumPoolSize(config.pool().maximumSize());
        pool.setMinimumIdle(config.pool().minimumIdle());
        pool.setConnectionTimeout(config.pool().acquisitionTimeout().toMillis());
    }
}
