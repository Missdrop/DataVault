package cn.missdrop.datavault.runtime;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.SqliteConfig;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class RegistryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void rejectsDuplicateFilesAndReturnsBudgetAfterUnregister() throws Exception {
        var vault = new DefaultDataVault(2, 2, 1024);
        var a = PluginId.of("a");
        var b = PluginId.of("b");
        var config = SqliteConfig.of(temporary.getRoot().toPath().resolve("a.db"));
        try {
            var database = vault.register(a, config).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(database, vault.find(a).orElseThrow());
            assertThrows(ExecutionException.class,
                    () -> vault.register(a, config).toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,
                    () -> vault.register(b, config).toCompletableFuture().get(5, TimeUnit.SECONDS));
            vault.unregister(a).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertFalse(vault.find(a).isPresent());
            vault.register(b, config).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, vault.owners().size());
        } finally {
            vault.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        assertTrue(vault.owners().isEmpty());
    }

    @Test
    public void globalBudgetRejectsExcessOwners() throws Exception {
        var vault = new DefaultDataVault(1, 1, 256);
        try {
            var file = temporary.getRoot().toPath();
            vault.register(PluginId.of("a"), SqliteConfig.of(file.resolve("a.db")))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> vault.register(PluginId.of("b"),
                    SqliteConfig.of(file.resolve("b.db"))).toCompletableFuture().get(5, TimeUnit.SECONDS));
        } finally {
            vault.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }
}
