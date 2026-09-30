package cn.missdrop.datavault.runtime;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.FileDatabaseConfig;
import cn.missdrop.datavault.api.config.SqliteConfig;
import cn.missdrop.datavault.api.config.h2.H2Config;
import cn.missdrop.datavault.api.config.duckdb.DuckDbConfig;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real embedded engines; neither JDBC behavior nor native file creation is mocked. */
public class EmbeddedBackendTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void sqliteUsesDedicatedWalFileAndRealTransactions() throws Exception {
        verify(SqliteConfig.of(temporary.getRoot().toPath().resolve("sqlite.db")));
    }

    @Test
    public void h2UsesDedicatedMvStoreAndRealTransactions() throws Exception {
        verify(H2Config.of(temporary.getRoot().toPath().resolve("h2-file")));
    }

    @Test
    public void duckDbUsesDedicatedNativeInstanceAndRealTransactions() throws Exception {
        verify(DuckDbConfig.of(temporary.getRoot().toPath().resolve("analytics.duckdb")));
    }

    private void verify(FileDatabaseConfig config) throws Exception {
        var vault = new DefaultDataVault(2, 2, 1024);
        try {
            var database = vault.register(PluginId.of("embedded"), config)
                    .toCompletableFuture().get(15, TimeUnit.SECONDS);
            database.execute(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE accounts (id INT PRIMARY KEY, balance INT)");
                    statement.execute("INSERT INTO accounts VALUES (1, 10)");
                }
                return null;
            }).toCompletableFuture().get(10, TimeUnit.SECONDS);
            var failed = database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("UPDATE accounts SET balance = 99");
                }
                throw new SQLException("Intentional rollback");
            }).toCompletableFuture();
            assertThrows(ExecutionException.class, () -> failed.get(10, TimeUnit.SECONDS));
            int value = database.execute(connection -> {
                assertTrue(connection.getAutoCommit());
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT balance FROM accounts WHERE id = 1")) {
                    rows.next();
                    return rows.getInt(1);
                }
            }).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(10, value);
            assertThrows(ExecutionException.class, () -> vault.register(PluginId.of("other"), config)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS));
            BatchWorkload.verify(database, "accounts", 1000);
        } finally {
            vault.shutdown().toCompletableFuture().get(15, TimeUnit.SECONDS);
        }
    }
}
