package cn.missdrop.datavault.api.config;

import java.nio.file.Path;

/** Dedicated embedded database file, protected against duplicate registrations. */
public interface FileDatabaseConfig extends DatabaseConfig {
    /** @return normalized absolute database path (base path for H2) */
    Path file();
}
