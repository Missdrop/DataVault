package cn.missdrop.datavault.smoke;

import cn.missdrop.datavault.api.DataVault;
import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.api.config.SqliteConfig;
import cn.missdrop.datavault.api.config.duckdb.DuckDbConfig;
import cn.missdrop.datavault.api.config.h2.H2Config;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/** Exercises actual native file drivers through the public API on a running server. */
final class EmbeddedSmoke {
    private final DataVault vault;
    private final Path folder;
    private final Logger logger;

    EmbeddedSmoke(DataVault vault, Path folder, Logger logger) {
        this.vault = vault;
        this.folder = folder;
        this.logger = logger;
    }

    CompletionStage<Void> run() {
        return verify("sqlite", SqliteConfig.of(folder.resolve("sqlite.db")))
                .thenCompose(ignored -> verify("h2", H2Config.of(folder.resolve("h2"))))
                .thenCompose(ignored -> verify("duckdb", DuckDbConfig.of(folder.resolve("duckdb.db"))));
    }

    private CompletionStage<Void> verify(String engine, DatabaseConfig config) {
        var owner = PluginId.of("smoke-" + engine);
        return vault.register(owner, config).thenCompose(database -> database.transaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS datavault_test_live (id INTEGER PRIMARY KEY)");
                statement.executeUpdate("DELETE FROM datavault_test_live");
                statement.executeUpdate("INSERT INTO datavault_test_live VALUES (42)");
                try (var rows = statement.executeQuery("SELECT id FROM datavault_test_live")) {
                    if (!rows.next() || rows.getInt(1) != 42) {
                        throw new IllegalStateException("Embedded round-trip mismatch");
                    }
                }
            }
            return null;
        })).thenCompose(ignored -> vault.unregister(owner))
                .thenRun(() -> logger.info("SMOKE PASS: " + engine + " transaction/CRUD/close"));
    }
}
