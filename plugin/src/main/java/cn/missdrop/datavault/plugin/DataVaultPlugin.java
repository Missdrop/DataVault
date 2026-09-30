package cn.missdrop.datavault.plugin;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DataVault's Paper plugin entry point.
 */
public final class DataVaultPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("DataVault has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DataVault has been disabled.");
    }
}
