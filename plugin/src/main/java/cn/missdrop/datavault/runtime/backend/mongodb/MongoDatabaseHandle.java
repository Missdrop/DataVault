package cn.missdrop.datavault.runtime.backend.mongodb;

import cn.missdrop.datavault.api.*;
import cn.missdrop.datavault.api.config.mongodb.MongoConfig;
import cn.missdrop.datavault.api.mongodb.*;
import cn.missdrop.datavault.runtime.execution.DatabaseExecutor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Native callback composition: no JDBC adaptation, JSON round-trip, or extra result copying. */
public final class MongoDatabaseHandle implements MongoStorage {
    private final PluginId owner;
    private final MongoDatabase database;
    private final DatabaseExecutor executor;

    MongoDatabaseHandle(PluginId owner, MongoConfig config, MongoClient client,
                        MongoDatabase database, Runnable released) {
        this.owner = owner;
        this.database = database;
        this.executor = new DatabaseExecutor(owner.value(), config.execution(), () -> {
            try {
                client.close();
            } finally {
                released.run();
            }
        });
    }

    @Override
    public PluginId owner() { return owner; }

    @Override
    public DatabaseType type() { return DatabaseType.MONGODB; }

    @Override
    public <T> CompletionStage<T> execute(MongoOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        return executor.submit(() -> operation.execute(database));
    }

    @Override
    public CompletionStage<Void> close() {
        return executor.close();
    }
}
