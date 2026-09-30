package cn.missdrop.datavault.api.config.h2;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.config.EmbeddedJdbcConfig;
import java.nio.file.Path;

/** H2 file configuration. The engine creates a .mv.db file beside the base path. */
public final class H2Config extends EmbeddedJdbcConfig {
    /** Creates a dedicated H2 file with conservative queue limits.
     * @param file dedicated H2 base path; the engine appends .mv.db
     * @param queueCapacity maximum waiting operations
     */
    public H2Config(Path file, int queueCapacity) {
        super(DatabaseType.H2, file, queueCapacity);
        // H2 interprets semicolons as URL settings, not literal filename characters.
        if (file().toString().contains(";")) {
            throw new IllegalArgumentException("H2 file paths must not contain JDBC option delimiters");
        }
    }

    /**
     * Returns default file configuration for this owner.
     * @return default file configuration for this owner
     * @param file dedicated H2 base path; the engine appends .mv.db
     */
    public static H2Config of(Path file) {
        return new H2Config(file, 256);
    }
}
