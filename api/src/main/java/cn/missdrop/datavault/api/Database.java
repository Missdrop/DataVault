package cn.missdrop.datavault.api;

import java.util.concurrent.CompletionStage;

/**
 * Per-plugin database handle with its own pool and bounded execution queue.
 * Callbacks and completion handlers may run on database workers. Game-state
 * changes require platform scheduling; never wait on the server thread.
 */
public interface Database {
    PluginId owner();
    DatabaseType type();

    /** Borrows a connection in auto-commit mode and returns it after the callback. */
    <T> CompletionStage<T> execute(SqlOperation<T> operation);

    /**
     * Commits on callback success and rolls back on failure. Rollback failures
     * are suppressed on the original error. The callback must not manage commits
     * or auto-commit. SQLite work is serialized per file.
     */
    <T> CompletionStage<T> transaction(SqlOperation<T> operation);

    /**
     * Rejects new operations, drains accepted work, then closes resources.
     * Idempotent and nonblocking; completion means resources have been released.
     * Cancellation of an operation stage does not guarantee JDBC cancellation.
     */
    CompletionStage<Void> close();
}
