package cn.missdrop.datavault.benchmark;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.*;
import cn.missdrop.datavault.api.config.mariadb.MariaDbConfig;
import cn.missdrop.datavault.api.config.postgresql.PostgresqlConfig;
import cn.missdrop.datavault.api.config.clickhouse.ClickHouseConfig;
import cn.missdrop.datavault.integration.DockerDatabase;
import cn.missdrop.datavault.runtime.JdbcDatabase;
import cn.missdrop.datavault.runtime.connection.PoolFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Identical SQL, pool and worker counts on each path; no cross-engine ranking is meaningful. */
public class JdbcBenchmarkDockerTest {
    private final PoolOptions pool = new PoolOptions(1, 1, Duration.ofSeconds(3));
    private final ExecutionOptions execution = new ExecutionOptions(1, 256);

    @Test public void mysql() throws Exception {
        try (var server = new DockerDatabase("mysql:8.4", 3306,
                "MYSQL_ROOT_PASSWORD=test-only", "MYSQL_DATABASE=datavault_test")) {
            ready(server, new MysqlConfig("jdbc:mysql://127.0.0.1:" + server.port() + "/datavault_test", "root", "test-only", pool, execution));
        }
    }

    @Test public void mariaDb() throws Exception {
        try (var server = new DockerDatabase("mariadb:11.4", 3306,
                "MARIADB_ROOT_PASSWORD=test-only", "MARIADB_DATABASE=datavault_test")) {
            ready(server, new MariaDbConfig("jdbc:mariadb://127.0.0.1:" + server.port() + "/datavault_test", "root", "test-only", pool, execution));
        }
    }

    @Test public void postgresql() throws Exception {
        try (var server = new DockerDatabase("postgres:17", 5432,
                "POSTGRES_PASSWORD=test-only", "POSTGRES_DB=datavault_test")) {
            ready(server, new PostgresqlConfig("jdbc:postgresql://127.0.0.1:" + server.port() + "/datavault_test", "postgres", "test-only", pool, execution));
        }
    }

    @Test public void clickHouse() throws Exception {
        try (var server = new DockerDatabase("clickhouse/clickhouse-server:25.8", 8123,
                "CLICKHOUSE_USER=test", "CLICKHOUSE_PASSWORD=test-only", "CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT=1")) {
            var config = new ClickHouseConfig("jdbc:clickhouse://127.0.0.1:" + server.port() + "/default", "test", "test-only", pool, execution);
            server.await(() -> { try (var source = new PoolFactory().open(PluginId.of("startup"), config)) { return true; } });
            compareClickHouse(config);
        }
    }

    private void ready(DockerDatabase server, DatabaseConfig config) throws Exception {
        server.await(() -> { try (var source = new PoolFactory().open(PluginId.of("startup"), config)) { return true; } });
        PerformanceComparisonTest.compare(config.type().name(), config);
    }

    /** ClickHouse uses append batches, not UPDATE transactions or artificial rollback emulation. */
    private void compareClickHouse(DatabaseConfig config) throws Exception {
        var source = new PoolFactory().open(PluginId.of("benchmark"), config);
        var database = new JdbcDatabase(PluginId.of("benchmark"), config, source);
        try (var direct = new DirectJdbcPath(source, false); var asynchronous = new DirectJdbcPath(source, true)) {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE benchmark (id INT, balance INT) ENGINE = MergeTree ORDER BY id");
                statement.execute("INSERT INTO benchmark VALUES (1, 0)");
            }
            var paths = Arrays.<BenchmarkPath>asList(direct, asynchronous, new VaultPath(database));
            ComparisonRunner.compare("ClickHouse", Workload.forTable("benchmark").get(0), paths);
            var batch = new Workload("append-100", 30, false, 100, connection -> {
                try (var insert = connection.prepareStatement("INSERT INTO benchmark VALUES (?, ?)")) {
                    for (int id = 0; id < 100; id++) {
                        insert.setInt(1, 2);
                        insert.setInt(2, id);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                    return 100;
                }
            });
            ComparisonRunner.compare("ClickHouse", batch, paths);
            try (var connection = source.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM benchmark")) {
                rows.next();
                org.junit.Assert.assertEquals(1 + 100 * 30 * 8 * 3, rows.getInt(1));
            }
        } finally {
            database.close().toCompletableFuture().get(30, TimeUnit.SECONDS);
        }
    }
}
