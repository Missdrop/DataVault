package cn.missdrop.datavault.benchmark;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.*;
import cn.missdrop.datavault.runtime.JdbcDatabase;
import cn.missdrop.datavault.runtime.connection.PoolFactory;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;

/** Opt-in local benchmark: no performance assertions depend on machine speed. */
public class PerformanceComparisonTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void sqliteComparison() throws Exception {
        compare("SQLite", SqliteConfig.of(temporary.getRoot().toPath().resolve("comparison.db")));
    }

    @Test
    public void mariaDbComparison() throws Exception {
        String user = System.getenv("DATAVAULT_TEST_USER");
        String password = System.getenv("DATAVAULT_TEST_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("Set DATAVAULT_TEST_PASSWORD for local benchmarks");
        }
        MysqlDataSource admin = new MysqlDataSource();
        admin.setUrl("jdbc:mysql://127.0.0.1:3306/?useSSL=false");
        try (var connection = admin.getConnection(user, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS datavault_test");
            System.out.println("SERVER," + connection.getMetaData().getDatabaseProductVersion());
        }
        compare("MariaDB", new MysqlConfig("jdbc:mysql://127.0.0.1:3306/datavault_test?useSSL=false",
                user, password, new PoolOptions(1, 1, Duration.ofSeconds(3)), new ExecutionOptions(1, 256)));
    }

    /** Shares the exact same pool across paths, eliminating differences in pool settings. */
    static void compare(String backend, DatabaseConfig config) throws Exception {
        String table = "datavault_test_bench_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var owner = PluginId.of("benchmark");
        var pool = new PoolFactory().open(owner, config);
        var database = new JdbcDatabase(owner, config, pool);
        try (var direct = new DirectJdbcPath(pool, false);
             var asynchronous = new DirectJdbcPath(pool, true)) {
            try (var connection = pool.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, balance INT)"
                        + (config.type() == cn.missdrop.datavault.api.DatabaseType.MYSQL ? " ENGINE=InnoDB" : ""));
                statement.execute("INSERT INTO " + table + " VALUES (1, 0)");
            }
            var paths = Arrays.<BenchmarkPath>asList(direct, asynchronous, new VaultPath(database));
            int expectedBalance = 0;
            var workloads = config instanceof FileDatabaseConfig ? Workload.forTable(table) : Workload.forNetworkTable(table);
            for (Workload workload : workloads) {
                ComparisonRunner.compare(backend, workload, paths);
                expectedBalance += workload.writes * workload.iterations
                        * (ComparisonRunner.WARMUP + ComparisonRunner.ROUNDS) * paths.size();
            }
            try (var connection = pool.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT balance FROM " + table + " WHERE id = 1")) {
                rows.next();
                assertEquals(expectedBalance, rows.getInt(1));
            }
        } finally {
            try (var connection = pool.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + table);
            } finally {
                database.close().toCompletableFuture().get(30, TimeUnit.SECONDS);
            }
        }
    }
}
