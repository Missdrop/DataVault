package cn.missdrop.datavault.benchmark;

import cn.missdrop.datavault.api.config.SqliteConfig;
import cn.missdrop.datavault.api.config.h2.H2Config;
import cn.missdrop.datavault.api.config.duckdb.DuckDbConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Part of the all-backend opt-in task; these three engines deliberately use real local files. */
public class EmbeddedBenchmarkDockerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void sqlite() throws Exception {
        PerformanceComparisonTest.compare("SQLite", SqliteConfig.of(temporary.getRoot().toPath().resolve("sqlite.db")));
    }

    @Test public void h2() throws Exception {
        PerformanceComparisonTest.compare("H2", H2Config.of(temporary.getRoot().toPath().resolve("h2")));
    }

    @Test public void duckDb() throws Exception {
        PerformanceComparisonTest.compare("DuckDB", DuckDbConfig.of(temporary.getRoot().toPath().resolve("duck.db")));
    }
}
