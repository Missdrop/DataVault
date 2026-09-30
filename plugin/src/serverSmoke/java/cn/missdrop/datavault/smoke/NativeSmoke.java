package cn.missdrop.datavault.smoke;

import cn.missdrop.datavault.api.DataVault;
import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.mongodb.MongoConfig;
import cn.missdrop.datavault.api.config.redis.RedisConfig;
import com.mongodb.client.MongoDatabase;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import org.bson.Document;

/** Verifies native callback ABI and CRUD against optional disposable Docker endpoints. */
final class NativeSmoke {
    private final DataVault vault;
    private final String mongoUri;
    private final String redisUri;
    private final Logger logger;

    NativeSmoke(DataVault vault, String mongoUri, String redisUri, Logger logger) {
        this.vault = vault;
        this.mongoUri = mongoUri;
        this.redisUri = redisUri;
        this.logger = logger;
    }

    CompletionStage<Void> run() {
        if (MongoDatabase.class.getClassLoader() != DataVault.class.getClassLoader()
                || RedisAsyncCommands.class.getClassLoader() != DataVault.class.getClassLoader()) {
            throw new IllegalStateException("Native callback type identity differs from DataVault's API loader");
        }
        logger.info("SMOKE PASS: shared MongoDB/Lettuce API class identity");
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        if (mongoUri != null) {
            chain = chain.thenCompose(ignored -> mongo());
        }
        if (redisUri != null) {
            chain = chain.thenCompose(ignored -> redis());
        }
        return chain;
    }

    private CompletionStage<Void> mongo() {
        var owner = PluginId.of("smoke-mongo");
        return vault.register(owner, MongoConfig.of(mongoUri, "datavault_test_live"))
                .thenCompose(storage -> storage.execute(database -> {
                    var collection = database.getCollection("datavault_test_live");
                    collection.deleteMany(new Document());
                    collection.insertOne(new Document("id", 42));
                    if (collection.countDocuments(new Document("id", 42)) != 1) {
                        throw new IllegalStateException("MongoDB round-trip mismatch");
                    }
                    collection.drop();
                    return null;
                })).thenCompose(ignored -> vault.unregister(owner))
                .thenRun(() -> logger.info("SMOKE PASS: MongoDB native callback/CRUD/close"));
    }

    private CompletionStage<Void> redis() {
        var owner = PluginId.of("smoke-redis");
        return vault.register(owner, RedisConfig.of(redisUri)).thenCompose(storage ->
                storage.execute(commands -> commands.set("datavault_test_live:value", "42"))
                        .thenCompose(ignored -> storage.execute(commands -> commands.get("datavault_test_live:value")))
                        .thenCompose(value -> {
                            if (!"42".equals(value)) {
                                throw new IllegalStateException("Redis round-trip mismatch");
                            }
                            return storage.execute(commands -> commands.del("datavault_test_live:value"));
                        })).thenCompose(ignored -> vault.unregister(owner))
                .thenRun(() -> logger.info("SMOKE PASS: Redis native callback/CRUD/close"));
    }
}
