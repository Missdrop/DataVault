package cn.missdrop.datavault.api.config;

import cn.missdrop.datavault.api.DatabaseType;
import java.nio.file.Path;
import java.util.Objects;

/** Single-owner embedded database with one writer and a retained connection. */
public abstract class EmbeddedJdbcConfig implements FileDatabaseConfig {
    private final Path file;
    private final DatabaseType type;
    private final ExecutionOptions execution;

    /** Normalizes file identity and serializes conflicting writes.
     * @param type concrete backend identity
     * @param file dedicated database file or H2 file base
     * @param queueCapacity maximum waiting operations
     */
    protected EmbeddedJdbcConfig(DatabaseType type, Path file, int queueCapacity) {
        this.type = type;
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.execution = new ExecutionOptions(1, queueCapacity);
    }

    @Override
    public final Path file() {
        return file;
    }

    @Override
    public final DatabaseType type() {
        return type;
    }

    @Override
    public final ExecutionOptions execution() {
        return execution;
    }
}
