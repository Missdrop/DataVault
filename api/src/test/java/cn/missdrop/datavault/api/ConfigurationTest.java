package cn.missdrop.datavault.api;

import cn.missdrop.datavault.api.config.ExecutionOptions;
import cn.missdrop.datavault.api.config.MysqlConfig;
import cn.missdrop.datavault.api.config.PoolOptions;
import cn.missdrop.datavault.api.config.SqliteConfig;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigurationTest {
    @Test
    public void backendSpecificConfigsRejectInvalidSchemesAndResources() {
        assertThrows(IllegalArgumentException.class, () ->
                cn.missdrop.datavault.api.config.postgresql.PostgresqlConfig.of(
                        "jdbc:mysql://localhost/db", "user", "password"));
        assertThrows(IllegalArgumentException.class, () ->
                new cn.missdrop.datavault.api.config.duckdb.DuckDbConfig(Path.of("analytics.db"), 256, 0, 256));
        assertFalse(cn.missdrop.datavault.api.config.clickhouse.ClickHouseConfig
                .of("jdbc:clickhouse://localhost:8123/default", "default", "").supportsTransactions());
        assertEquals(1, cn.missdrop.datavault.api.config.h2.H2Config.of(Path.of("local"))
                .connectionBudget());
        assertThrows(IllegalArgumentException.class, () ->
                cn.missdrop.datavault.api.config.h2.H2Config.of(Path.of("local;CACHE_SIZE=1024")));
        // Some filesystems reject '?' before configuration; Unix reaches the JDBC-path guard.
        assertThrows(IllegalArgumentException.class, () -> SqliteConfig.of(Path.of("local?cache=shared")));
    }

    @Test
    public void ownerHasValueEqualityAndRejectsUnsafeIdentifiers() {
        assertEquals(PluginId.of("economy"), PluginId.of("economy"));
        assertEquals(PluginId.of("economy").hashCode(), PluginId.of("economy").hashCode());
        for (String value : new String[] {"", "../escape", "Economy", "a/b", "a b"}) {
            assertThrows(IllegalArgumentException.class, () -> PluginId.of(value));
        }
    }

    @Test
    public void sqliteUsesNormalizedFileAndSingleWorker() {
        Path file = Path.of("storage", "economy", "..", "economy.db");
        SqliteConfig config = SqliteConfig.of(file);
        assertEquals(file.toAbsolutePath().normalize(), config.file());
        assertEquals(DatabaseType.SQLITE, config.type());
        assertEquals(1, config.execution().workers());
        assertTrue(config.wal());
        assertThrows(IllegalArgumentException.class,
                () -> new SqliteConfig(file, Duration.ofMillis(-1), true, 10));
    }

    @Test
    public void mysqlEnforcesBoundedPoolAndWorkerSizing() {
        MysqlConfig config = MysqlConfig.of("jdbc:mysql://localhost/economy", "user", "secret");
        assertEquals(DatabaseType.MYSQL, config.type());
        assertFalse(config.toString().contains("secret"));
        assertThrows(IllegalArgumentException.class,
                () -> MysqlConfig.of("jdbc:sqlite:data.db", "user", "secret"));
        assertThrows(IllegalArgumentException.class,
                () -> new PoolOptions(2, 3, Duration.ofSeconds(3)));
        assertThrows(IllegalArgumentException.class,
                () -> new PoolOptions(2, 0, Duration.ofMillis(249)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionOptions(1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MysqlConfig("jdbc:mysql://localhost/db", "user", "secret",
                        new PoolOptions(1, 0, Duration.ofSeconds(3)), new ExecutionOptions(2, 10)));
    }
}
