package cn.missdrop.datavault.runtime;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.MysqlConfig;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/** Opt-in: only creates its dedicated database and prefixed test table. */
public class MariaDbIntegrationTest {
    @Test
    public void mysqlDriverCommitsAndRollsBackOnLocalMariaDb() throws Exception {
        String user = System.getenv("DATAVAULT_TEST_USER");
        String password = System.getenv("DATAVAULT_TEST_PASSWORD");
        assertNotNull("Set DATAVAULT_TEST_PASSWORD", password);
        assertFalse("Set DATAVAULT_TEST_PASSWORD", password.isEmpty());
        MysqlDataSource admin = new MysqlDataSource();
        admin.setUrl("jdbc:mysql://127.0.0.1:3306/?useSSL=false&connectTimeout=3000&socketTimeout=10000");
        try (var connection = admin.getConnection(user, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS datavault_test");
            System.out.println("MariaDB version: " + connection.getMetaData().getDatabaseProductVersion());
        }
        var vault = new DefaultDataVault(6, 4, 1024);
        var config = MysqlConfig.of("jdbc:mysql://127.0.0.1:3306/datavault_test?useSSL=false", user, password);
        String table = "datavault_test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var database = vault.register(PluginId.of("mysql-test"), config)
                .toCompletableFuture().get(15, TimeUnit.SECONDS);
        try {
            database.execute(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, balance INT) ENGINE=InnoDB");
                    statement.execute("INSERT INTO " + table + " VALUES (1, 0)");
                }
                return null;
            }).toCompletableFuture().get(10, TimeUnit.SECONDS);
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE " + table + " SET balance = 10");
                }
                return null;
            }).toCompletableFuture().get(10, TimeUnit.SECONDS);
            var failure = database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE " + table + " SET balance = 99");
                }
                throw new SQLException("Expected rollback");
            }).toCompletableFuture();
            assertThrows(ExecutionException.class, () -> failure.get(10, TimeUnit.SECONDS));
            int value = database.execute(connection -> {
                assertTrue(connection.getAutoCommit());
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT balance FROM " + table)) {
                    rows.next();
                    return rows.getInt(1);
                }
            }).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(10, value);
        } finally {
            try {
                database.execute(connection -> {
                    try (var statement = connection.createStatement()) {
                        statement.execute("DROP TABLE IF EXISTS " + table);
                    }
                    return null;
                }).toCompletableFuture().get(10, TimeUnit.SECONDS);
            } finally {
                vault.shutdown().toCompletableFuture().get(15, TimeUnit.SECONDS);
            }
        }
    }
}
