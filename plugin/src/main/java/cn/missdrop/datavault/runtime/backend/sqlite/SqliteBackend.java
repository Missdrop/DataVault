package cn.missdrop.datavault.runtime.backend.sqlite;

import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.api.config.SqliteConfig;
import cn.missdrop.datavault.runtime.connection.JdbcBackend;
import com.zaxxer.hikari.HikariConfig;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** Reuses a single writer connection and bounded page cache without weakening durability. */
public final class SqliteBackend implements JdbcBackend {
    @Override
    public void configure(DatabaseConfig config, HikariConfig pool) {
        SqliteConfig sqlite = (SqliteConfig) config;
        SQLiteConfig settings = new SQLiteConfig();
        settings.setBusyTimeout((int) sqlite.busyTimeout().toMillis());
        settings.enforceForeignKeys(true);
        settings.setCacheSize(-8192); // KiB: cache pages, not an eager 8 MiB allocation.
        settings.setJournalMode(sqlite.wal()
                ? SQLiteConfig.JournalMode.WAL : SQLiteConfig.JournalMode.DELETE);
        SQLiteDataSource source = new SQLiteDataSource(settings);
        source.setUrl("jdbc:sqlite:" + sqlite.file());
        pool.setDataSource(source);
    }
}
