package cn.missdrop.datavault.api.config;

import cn.missdrop.datavault.api.DatabaseType;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Dedicated local file. Runtime providers must reject owners sharing an active
 * file, including symlink aliases, and initialize every connection consistently.
 */
public final class SqliteConfig implements FileDatabaseConfig {
    private final Path file;
    private final Duration busyTimeout;
    private final boolean wal;
    private final ExecutionOptions execution;

    /**
     * Validates and stores immutable settings before resources are allocated.
     * @param file local SQLite file
     * @param busyTimeout nonnegative SQLite lock wait
     * @param wal whether to enable WAL mode
     * @param queueCapacity positive waiting-task limit
     * @throws IllegalArgumentException if settings exceed their supported bounds
     */
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

    /**
     * Creates WAL-enabled local storage with one worker and a bounded queue.
     * @param file local database path
     * @return validated immutable configuration
     */
    public static SqliteConfig of(Path file) {
        return new SqliteConfig(file, Duration.ofSeconds(5), true, 256);
    }

    /**
     * Returns normalized absolute local database file.
     * @return normalized absolute local database file
     */
    public Path file() {
        return file;
    }
    /**
     * Returns maximum SQLite lock wait per connection.
     * @return maximum SQLite lock wait per connection
     */
    public Duration busyTimeout() {
        return busyTimeout;
    }
    /**
     * Returns whether write-ahead logging is enabled.
     * @return whether write-ahead logging is enabled
     */
    public boolean wal() {
        return wal;
    }

    @Override
    public DatabaseType type() { return DatabaseType.SQLITE; }

    @Override
    /**
     * Returns execution.
     * @return execution
     */
    public ExecutionOptions execution() {
        return execution;
    }
}
