package cn.missdrop.datavault.runtime;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.SqliteConfig;
import cn.missdrop.datavault.runtime.connection.PoolFactory;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class JdbcDatabaseTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void sqliteCommitsRollsBackAndReusesConnectionState() throws Exception {
        var owner = PluginId.of("sqlite-test");
        var config = SqliteConfig.of(temporary.getRoot().toPath().resolve("test.db"));
        var pool = new PoolFactory().open(owner, config);
        var database = new JdbcDatabase(owner, config, pool);
        try {
            database.execute(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE accounts (id INTEGER PRIMARY KEY, balance INTEGER)");
                    statement.execute("INSERT INTO accounts VALUES (1, 0)");
                }
                return null;
            }).toCompletableFuture().get(5, TimeUnit.SECONDS);
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE accounts SET balance = 10");
                }
                return null;
            }).toCompletableFuture().get(5, TimeUnit.SECONDS);
            var failed = database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE accounts SET balance = 20");
                }
                throw new SQLException("Intentional rollback");
            }).toCompletableFuture();
            assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
            int balance = database.execute(connection -> {
                assertTrue(connection.getAutoCommit());
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT balance FROM accounts")) {
                    rows.next();
                    return rows.getInt(1);
                }
            }).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(10, balance);
            String mode = database.execute(connection -> {
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("PRAGMA journal_mode")) {
                    rows.next();
                    return rows.getString(1);
                }
            }).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals("wal", mode);
            BatchWorkload.verify(database, "accounts", 5000);
        } finally {
            database.close().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        assertTrue(pool.isClosed());
    }
}
