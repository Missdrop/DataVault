package cn.missdrop.datavault.smoke;

import cn.missdrop.datavault.api.DataVault;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/** Separate consumer: its JAR intentionally contains no API or third-party driver classes. */
public final class SmokePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        DataVault vault = getServer().getServicesManager().load(DataVault.class);
        if (vault == null) {
            throw new IllegalStateException("DataVault service was not registered");
        }
        getLogger().info("SMOKE PASS: cross-plugin service lookup");
        var embedded = new EmbeddedSmoke(vault, getDataFolder().toPath(), getLogger());
        var nativeClients = new NativeSmoke(vault, getConfig().getString("mongo-uri"),
                getConfig().getString("redis-uri"), getLogger());
        // No Bukkit scheduler, joins, world access or platform calls from database callbacks.
        embedded.run().thenCompose(ignored -> nativeClients.run()).whenComplete((ignored, failure) -> {
            if (failure == null) {
                getLogger().info("SMOKE PASS: all configured backends completed and unregistered");
            } else {
                getLogger().log(Level.SEVERE, "SMOKE FAIL", failure);
            }
        });
    }
}
