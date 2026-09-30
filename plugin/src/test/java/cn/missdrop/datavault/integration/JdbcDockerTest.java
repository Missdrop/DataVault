package cn.missdrop.datavault.integration;

import cn.missdrop.datavault.api.Database;
import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.api.config.MysqlConfig;
import cn.missdrop.datavault.api.config.mariadb.MariaDbConfig;
import cn.missdrop.datavault.api.config.postgresql.PostgresqlConfig;
import cn.missdrop.datavault.api.config.clickhouse.ClickHouseConfig;
import cn.missdrop.datavault.runtime.DefaultDataVault;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises production drivers against actual servers, including transaction capability boundaries. */
public class JdbcDockerTest {
    @Test public void mysql() throws Exception {
        try (var server = new DockerDatabase("mysql:8.4", 3306,
                "MYSQL_ROOT_PASSWORD=test-only", "MYSQL_DATABASE=datavault_test")) {
            verify(server, MysqlConfig.of("jdbc:mysql://127.0.0.1:" + server.port() + "/datavault_test", "root", "test-only"));
        }
    }

    @Test public void mariaDb() throws Exception {
        try (var server = new DockerDatabase("mariadb:11.4", 3306,
                "MARIADB_ROOT_PASSWORD=test-only", "MARIADB_DATABASE=datavault_test")) {
            verify(server, MariaDbConfig.of("jdbc:mariadb://127.0.0.1:" + server.port() + "/datavault_test", "root", "test-only"));
        }
    }

    @Test public void postgresql() throws Exception {
        try (var server = new DockerDatabase("postgres:17", 5432,
                "POSTGRES_PASSWORD=test-only", "POSTGRES_DB=datavault_test")) {
            verify(server, PostgresqlConfig.of("jdbc:postgresql://127.0.0.1:" + server.port() + "/datavault_test", "postgres", "test-only"));
        }
    }

    @Test public void clickHouse() throws Exception {
        try (var server = new DockerDatabase("clickhouse/clickhouse-server:25.8", 8123,
                "CLICKHOUSE_USER=test", "CLICKHOUSE_PASSWORD=test-only", "CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT=1")) {
            verify(server, ClickHouseConfig.of("jdbc:clickhouse://127.0.0.1:" + server.port() + "/default", "test", "test-only"));
        }
    }

    private void verify(DockerDatabase server, DatabaseConfig config) throws Exception {
        var vault = new DefaultDataVault(12, 8, 2048);
        try {
            Database database = server.await(() -> vault.register(PluginId.of("first"), config)
                    .toCompletableFuture().get(15, TimeUnit.SECONDS));
            String suffix = config.supportsTransactions() ? "" : " ENGINE = MergeTree ORDER BY id";
            database.execute(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE accounts (id INT, balance INT)" + suffix);
                }
                try (var insert = connection.prepareStatement("INSERT INTO accounts VALUES (?, ?)")) {
                    for (int id = 1; id <= 100; id++) {
                        insert.setInt(1, id);
                        insert.setInt(2, 10);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                return null;
            }).toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertEquals(100, (int) database.execute(connection -> {
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM accounts")) {
                    rows.next();
                    return rows.getInt(1);
                }
            }).toCompletableFuture().get(10, TimeUnit.SECONDS));
            if (config.supportsTransactions()) {
                var failed = database.transaction(connection -> {
                    try (var statement = connection.createStatement()) {
                        statement.executeUpdate("UPDATE accounts SET balance = 99");
                    }
                    throw new SQLException("Intentional rollback");
                }).toCompletableFuture();
                assertThrows(ExecutionException.class, () -> failed.get(10, TimeUnit.SECONDS));
                assertEquals(1000, (int) database.execute(connection -> {
                    assertTrue(connection.getAutoCommit());
                    try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT SUM(balance) FROM accounts")) {
                        rows.next();
                        return rows.getInt(1);
                    }
                }).toCompletableFuture().get(10, TimeUnit.SECONDS));
            } else {
                var failed = database.transaction(connection -> { fail("Unsupported callback must not execute"); return null; }).toCompletableFuture();
                assertThrows(ExecutionException.class, () -> failed.get(10, TimeUnit.SECONDS));
            }
            assertTrue(vault.find(PluginId.of("first")).isPresent());
            verifyIsolation(vault, database, config);
        } finally {
            vault.shutdown().toCompletableFuture().get(20, TimeUnit.SECONDS);
        }
    }

    /** Waiting callbacks exhaust one owner's workers, never another owner's pool or queue. */
    private void verifyIsolation(DefaultDataVault vault, Database first, DatabaseConfig config) throws Exception {
        var second = vault.register(PluginId.of("second"), config).toCompletableFuture().get(15, TimeUnit.SECONDS);
        var entered = new java.util.concurrent.CountDownLatch(config.execution().workers());
        var release = new java.util.concurrent.CountDownLatch(1);
        var pending = new java.util.ArrayList<java.util.concurrent.CompletableFuture<?>>();
        try {
            for (int worker = 0; worker < config.execution().workers(); worker++) {
                pending.add(first.execute(connection -> {
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Isolation callback interrupted", failure);
                    }
                    return null;
                }).toCompletableFuture());
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(1, (int) second.execute(connection -> {
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT 1")) {
                    rows.next();
                    return rows.getInt(1);
                }
            }).toCompletableFuture().get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            for (var operation : pending) {
                operation.get(5, TimeUnit.SECONDS);
            }
        }
    }
}
