package cn.missdrop.datavault.plugin;

import cn.missdrop.datavault.api.DataVault;
import cn.missdrop.datavault.bootstrap.DependencyBootstrap;
import cn.missdrop.datavault.runtime.DefaultDataVault;
import java.util.logging.Level;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit entry point; only platform service registration belongs in this class.
 */
public final class DataVaultPlugin extends JavaPlugin {
    private DataVault vault;
    private boolean librariesReady;

    @Override
    public void onLoad() {
        try {
            // Attach native APIs to this loader before dependent plugins can resolve callback types.
            DependencyBootstrap.install(getFile().toPath(), getLogger(), getClass().getClassLoader());
            librariesReady = true;
        } catch (java.io.IOException | RuntimeException failure) {
            getLogger().log(Level.SEVERE, "Cannot prepare Maven dependencies; DataVault will not enable. "
                    + "Check connectivity to Maven Central and write access to DataVault/libraries.", failure);
        }
    }

    @Override
    public void onEnable() {
        if (!librariesReady) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        // Reservations cap aggregate resources even when many plugins register simultaneously.
        vault = new DefaultDataVault(getConfig().getInt("limits.connections", 48),
                getConfig().getInt("limits.workers", 32), getConfig().getLong("limits.queued-tasks", 8192));
        getServer().getServicesManager().register(DataVault.class, vault, this, ServicePriority.Normal);
        getLogger().info("DataVault has been enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (vault != null) {
            // Do not join on the server thread: in-flight operations drain on database workers.
            vault.shutdown().whenComplete((ignored, failure) -> {
                if (failure != null) {
                    getLogger().log(Level.SEVERE, "Failed to close database resources", failure);
                }
            });
        }
        getLogger().info("DataVault has been disabled.");
    }
}
