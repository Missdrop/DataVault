package cn.missdrop.datavault.runtime.registry;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.api.config.SqliteConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Tracks canonical local SQLite paths independently of pool and registry logic. */
public final class SqliteFiles {
    private final Map<Path, PluginId> owners = new HashMap<>();

    /** Performs filesystem I/O before acquiring the ownership lock. */
    public void reserve(PluginId owner, DatabaseConfig config) throws Exception {
        if (!(config instanceof SqliteConfig)) {
            return;
        }
        Path path = ((SqliteConfig) config).file();
        Files.createDirectories(path.getParent());
        // Normalize is insufficient: symlink aliases must resolve to the same ownership key.
        Path canonical = Files.exists(path) ? path.toRealPath()
                : path.getParent().toRealPath().resolve(path.getFileName());
        synchronized (owners) {
            if (owners.containsKey(canonical)) {
                throw new IllegalStateException("SQLite file already registered");
            }
            owners.put(canonical, owner);
        }
    }

    /** Called only after removing the matching registration; file contents are retained. */
    public void release(PluginId owner) {
        synchronized (owners) {
            owners.values().removeIf(owner::equals);
        }
    }
}
