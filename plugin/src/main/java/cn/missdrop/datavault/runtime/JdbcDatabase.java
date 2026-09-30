package cn.missdrop.datavault.runtime;

import cn.missdrop.datavault.api.*;
import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.runtime.execution.DatabaseExecutor;
import cn.missdrop.datavault.runtime.transaction.Transactions;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Composes pool ownership, task admission and transaction boundaries. */
public final class JdbcDatabase implements Database {
    private final PluginId owner;
    private final DatabaseType type;
    private final HikariDataSource pool;
    private final boolean transactions;
    private final DatabaseExecutor executor;

    /** Takes ownership of a validated pool and connects its cleanup to worker termination. */
    public JdbcDatabase(PluginId owner, DatabaseConfig config, HikariDataSource pool) {
        this(owner, config, pool, () -> {});
    }

    /** Registry cleanup completes before close() reports success to the owner. */
    JdbcDatabase(PluginId owner, DatabaseConfig config, HikariDataSource pool, Runnable released) {
        this.owner = owner;
        this.type = config.type();
        this.pool = pool;
        this.transactions = config.supportsTransactions();
        this.executor = new DatabaseExecutor(owner.value(), config.execution(), () -> {
            try {
                pool.close();
            } finally {
                released.run();
            }
        });
    }

    @Override
    public PluginId owner() {
        return owner;
    }

    @Override
    public DatabaseType type() {
        return type;
    }

    @Override
    public <T> CompletionStage<T> execute(SqlOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        return executor.submit(() -> {
            try (Connection connection = pool.getConnection()) {
                // Hikari's close returns this borrowed connection; it does not close the pool.
                return operation.execute(connection);
            }
        });
    }

    @Override
    public <T> CompletionStage<T> transaction(SqlOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        if (!transactions) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new UnsupportedOperationException(type + " does not support JDBC transactions"));
        }
        return executor.submit(() -> {
            try (Connection connection = pool.getConnection()) {
                return Transactions.run(connection, operation);
            }
        });
    }

    @Override
    public CompletionStage<Void> close() {
        return executor.close();
    }

    /** Used by the registry to release ownership only after pool cleanup. */
    public CompletionStage<Void> whenClosed() {
        return executor.whenClosed();
    }
}
