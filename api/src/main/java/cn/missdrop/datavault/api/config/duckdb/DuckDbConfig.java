package cn.missdrop.datavault.api.config.duckdb;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.config.EmbeddedJdbcConfig;
import java.nio.file.Path;

/** DuckDB analytics instance. Native threads and memory are limited per file. */
public final class DuckDbConfig extends EmbeddedJdbcConfig {
    private final int nativeThreads;
    private final int memoryLimitMb;

    /** Prevents each plugin from starting an unbounded native analytics engine.
     * @param file dedicated database file or H2 file base
     * @param queueCapacity maximum waiting operations
     * @param nativeThreads maximum DuckDB execution threads
     * @param memoryLimitMb DuckDB memory limit in MiB
     */
    public DuckDbConfig(Path file, int queueCapacity, int nativeThreads, int memoryLimitMb) {
        super(DatabaseType.DUCKDB, file, queueCapacity);
        if (nativeThreads < 1 || memoryLimitMb < 16) {
            throw new IllegalArgumentException("Require positive native threads and at least 16 MiB");
        }
        this.nativeThreads = nativeThreads;
        this.memoryLimitMb = memoryLimitMb;
    }

    /**
     * Returns dedicated file with two native threads and a 256 MiB engine budget.
     * @return dedicated file with two native threads and a 256 MiB engine budget
     * @param file dedicated database file or H2 file base
     */
    public static DuckDbConfig of(Path file) {
        return new DuckDbConfig(file, 256, 2, 256);
    }

    /**
     * Returns native query threads, separate from Java worker limits.
     * @return native query threads, separate from Java worker limits
     */
    public int nativeThreads() {
        return nativeThreads;
    }

    /**
     * Returns engine memory budget in MiB; not a total process RSS guarantee.
     * @return engine memory budget in MiB; not a total process RSS guarantee
     */
    public int memoryLimitMb() {
        return memoryLimitMb;
    }
}
