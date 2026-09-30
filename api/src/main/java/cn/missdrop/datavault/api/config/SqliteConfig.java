package cn.missdrop.datavault.api.config;

import cn.missdrop.datavault.api.DatabaseType;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Dedicated local file. Runtime providers must reject owners sharing an active
 * file, including symlink aliases, and initialize every connection consistently.
 */
public final class SqliteConfig implements DatabaseConfig {
    private final Path file;
    private final Duration busyTimeout;
    private final boolean wal;
    private final ExecutionOptions execution;

    public SqliteConfig(Path file, Duration busyTimeout, boolean wal, int queueCapacity) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.busyTimeout = Objects.requireNonNull(busyTimeout, "busyTimeout");
        if (busyTimeout.isNegative()
                || busyTimeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Busy timeout must fit a nonnegative millisecond integer");
        }
        this.wal = wal;
        // One worker avoids per-file write contention in the initial implementation.
        this.execution = new ExecutionOptions(1, queueCapacity);
    }

    public static SqliteConfig of(Path file) {
        return new SqliteConfig(file, Duration.ofSeconds(5), true, 256);
    }

    public Path file() { return file; }
    public Duration busyTimeout() { return busyTimeout; }
    public boolean wal() { return wal; }

    @Override
    public DatabaseType type() { return DatabaseType.SQLITE; }

    @Override
    public ExecutionOptions execution() { return execution; }
}
