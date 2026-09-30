package cn.missdrop.datavault.runtime.backend.mongodb;

import cn.missdrop.datavault.api.PluginId;
import cn.missdrop.datavault.api.config.mongodb.MongoConfig;
import com.mongodb.*;
import com.mongodb.client.MongoClients;
import java.util.concurrent.TimeUnit;
import org.bson.Document;

/** Creates one MongoClient per owner; the driver manages its native pools. */
public final class MongoBackend {
    /** Validates connectivity before publishing a handle, closing on partial failure. */
    public MongoDatabaseHandle open(PluginId owner, MongoConfig config, Runnable released) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .applyToSocketSettings(builder -> builder.connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS))
                .applyConnectionString(new ConnectionString(config.uri()))
                .applicationName("DataVault-" + owner.value())
                .applyToConnectionPoolSettings(builder -> builder.maxSize(config.pool().maximumSize())
                        .minSize(config.pool().minimumIdle())
                        .maxWaitTime(config.pool().acquisitionTimeout().toMillis(), TimeUnit.MILLISECONDS))
                .build();
        var client = MongoClients.create(settings);
        try {
            var database = client.getDatabase(config.database());
            database.runCommand(new Document("ping", 1));
            return new MongoDatabaseHandle(owner, config, client, database, released);
        } catch (Throwable failure) {
            client.close();
            throw failure;
        }
    }
}
