package cn.missdrop.datavault.api;

import cn.missdrop.datavault.api.config.DatabaseConfig;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Platform-independent registry. Opening and closing must not block callers. */
public interface DataVault {
    /** Opens an isolated database; duplicate owners fail and failed opens release resources. */
    CompletionStage<Database> register(PluginId owner, DatabaseConfig config);

    /** Returns a registered database without opening connections. */
    Optional<Database> find(PluginId owner);

    /** Returns an immutable snapshot of registered owners. */
    Set<PluginId> owners();

    /** Drains and closes the owner's database, then removes it. Unknown owners are a no-op. */
    CompletionStage<Void> unregister(PluginId owner);

    /** Rejects new registrations and asynchronously closes all databases. */
    CompletionStage<Void> shutdown();
}
