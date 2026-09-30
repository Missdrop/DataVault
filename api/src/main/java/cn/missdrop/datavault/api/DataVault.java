package cn.missdrop.datavault.api;

import cn.missdrop.datavault.api.config.DatabaseConfig;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Platform-independent registry. Opening and closing must not block callers. */
public interface DataVault {
    /**
     * Opens an isolated database; duplicate owners fail and failed opens release resources.
     * @param owner stable plugin identifier
     * @param config immutable backend settings
     * @return stage completing when the database is ready
     */
    CompletionStage<Database> register(PluginId owner, DatabaseConfig config);

    /**
     * Returns a registered database without opening connections.
     * @param owner stable plugin identifier
     * @return ready database, or empty while opening or after removal
     */
    Optional<Database> find(PluginId owner);

    /**
     * Returns an immutable snapshot of registered owners.
     * @return detached snapshot of ready owners
     */
    Set<PluginId> owners();

    /**
     * Drains and closes the owner's database, then removes it. Unknown owners are a no-op.
     * @param owner stable plugin identifier
     * @return stage completing after closure and removal
     */
    CompletionStage<Void> unregister(PluginId owner);

    /**
     * Rejects new registrations and asynchronously closes all databases.
     * @return stage completing after all resources are closed
     */
    CompletionStage<Void> shutdown();
}
