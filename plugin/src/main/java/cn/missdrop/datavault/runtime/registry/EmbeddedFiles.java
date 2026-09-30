package cn.missdrop.datavault.runtime.registry;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.StorageConfig;
import cn.missdrop.datavault.api.config.FileDatabaseConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Tracks canonical embedded database files independently of pool and registry logic. */
public final class EmbeddedFiles {
    private final Map<Path, PluginId> owners = new HashMap<>();

    /** Performs filesystem I/O before acquiring the ownership lock. */
    public void reserve(PluginId owner, StorageConfig<?> config) throws Exception {
        if (!(config instanceof FileDatabaseConfig)) {
            return;
        }
        Path path = ((FileDatabaseConfig) config).file();
        // H2's physical file uses this suffix; reserve the actual file, not just its URL base.
        if (config.type() == cn.missdrop.datavault.api.DatabaseType.H2) {
            path = path.resolveSibling(path.getFileName() + ".mv.db");
        }
        Files.createDirectories(path.getParent());
        // Normalize is insufficient: symlink aliases must resolve to the same ownership key.
        Path canonical = Files.exists(path) ? path.toRealPath()
                : path.getParent().toRealPath().resolve(path.getFileName());
        synchronized (owners) {
            if (owners.containsKey(canonical)) {
                throw new IllegalStateException("Embedded database file already registered");
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
