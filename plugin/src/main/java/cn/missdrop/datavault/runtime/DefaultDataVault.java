package cn.missdrop.datavault.runtime;

import cn.missdrop.datavault.api.*;
import cn.missdrop.datavault.api.config.DatabaseConfig;
import cn.missdrop.datavault.runtime.connection.PoolFactory;
import cn.missdrop.datavault.runtime.registry.ResourceBudget;
import cn.missdrop.datavault.runtime.registry.SqliteFiles;
import java.util.*;
import java.util.concurrent.*;

/**
 * Asynchronous registry. Its small lock protects reservations only; connection
 * creation, callbacks and pool shutdown never run while holding it.
 */
public final class DefaultDataVault implements DataVault {
    private final Object lock = new Object();
    private final Map<PluginId, Entry> entries = new HashMap<>();
    private final SqliteFiles files = new SqliteFiles();
    private final PoolFactory pools = new PoolFactory();
    private final ResourceBudget budget;
    private final ThreadPoolExecutor lifecycle;
    private CompletableFuture<Void> shutdown;

    /** Creates a registry with global limits; pending registrations also consume budget. */
    public DefaultDataVault(int connections, int workers, long queuedTasks) {
        budget = new ResourceBudget(connections, workers, queuedTasks);
        lifecycle = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), task -> {
                    Thread thread = new Thread(task, "DataVault-lifecycle");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        lifecycle.allowCoreThreadTimeOut(true);
    }

    @Override
    /** Reserves resources before scheduling I/O, preventing concurrent oversubscription. */
    public CompletionStage<Database> register(PluginId owner, DatabaseConfig config) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(config, "config");
        Entry entry = new Entry(config);
        synchronized (lock) {
            if (shutdown != null || entries.containsKey(owner)) {
                return CompletableFuture.failedFuture(new IllegalStateException("Registry closed or owner registered"));
            }
            try {
                budget.reserve(config);
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
            entries.put(owner, entry);
        }
        // The lock is released before submitting: pool construction can block on network I/O.
        try {
            lifecycle.execute(() -> open(owner, entry));
        } catch (RejectedExecutionException failure) {
            release(owner, entry);
            entry.ready.completeExceptionally(failure);
        }
        return entry.ready.thenApply(database -> database);
    }

    /** Opens the pool off-thread and releases every reservation on partial failure. */
    private void open(PluginId owner, Entry entry) {
        try {
            files.reserve(owner, entry.config);
            var pool = pools.open(owner, entry.config);
            JdbcDatabase database;
            try {
                database = new JdbcDatabase(owner, entry.config, pool, () -> release(owner, entry));
            } catch (Throwable failure) {
                pool.close();
                throw failure;
            }
            // The database releases its reservation before completing a direct close().
            entry.ready.complete(database);
        } catch (Throwable failure) {
            release(owner, entry);
            entry.ready.completeExceptionally(failure);
        }
    }

    /** Identity-based removal makes repeated cleanup safe without releasing a newer entry. */
    private void release(PluginId owner, Entry entry) {
        synchronized (lock) {
            if (entries.remove(owner, entry)) {
                budget.release(entry.config);
                files.release(owner);
            }
        }
    }

    @Override
    /** Opening registrations are deliberately absent until validation has succeeded. */
    public Optional<Database> find(PluginId owner) {
        synchronized (lock) {
            Entry entry = entries.get(owner);
            return entry == null || entry.ready.isCompletedExceptionally()
                    ? Optional.empty() : Optional.ofNullable(entry.ready.getNow(null));
        }
    }

    @Override
    /** Returns a detached snapshot, so callers cannot mutate internal registration state. */
    public Set<PluginId> owners() {
        synchronized (lock) {
            Set<PluginId> result = new HashSet<>();
            entries.forEach((owner, entry) -> {
                if (entry.ready.isDone() && !entry.ready.isCompletedExceptionally()) {
                    result.add(owner);
                }
            });
            return Collections.unmodifiableSet(result);
        }
    }

    @Override
    /** Waits for an in-flight open before closing; no pool can escape shutdown through a race. */
    public CompletionStage<Void> unregister(PluginId owner) {
        Entry entry;
        synchronized (lock) {
            entry = entries.get(owner);
        }
        if (entry == null) {
            return CompletableFuture.completedFuture(null);
        }
        return entry.ready.handle((database, failure) -> database)
                .thenCompose(database -> database == null
                        ? CompletableFuture.completedFuture(null) : database.close())
                .whenComplete((ignored, failure) -> release(owner, entry));
    }

    @Override
    /** Marks the registry closed before taking the snapshot, preventing late registrations. */
    public CompletionStage<Void> shutdown() {
        Set<PluginId> owners;
        synchronized (lock) {
            if (shutdown != null) {
                return shutdown.minimalCompletionStage();
            }
            shutdown = new CompletableFuture<>();
            owners = new HashSet<>(entries.keySet());
        }
        CompletableFuture<?>[] closing = owners.stream()
                .map(owner -> unregister(owner).toCompletableFuture()).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(closing).whenComplete((ignored, failure) -> {
            lifecycle.shutdown();
            if (failure == null) {
                shutdown.complete(null);
            } else {
                shutdown.completeExceptionally(failure);
            }
        });
        return shutdown.minimalCompletionStage();
    }

    /** Owner reservation persists from admission until pool cleanup finishes. */
    private static final class Entry {
        final DatabaseConfig config;
        final CompletableFuture<JdbcDatabase> ready = new CompletableFuture<>();

        Entry(DatabaseConfig config) {
            this.config = config;
        }
    }
}
