package cn.missdrop.datavault.runtime.execution;

import cn.missdrop.datavault.api.exception.DatabaseOverloadedException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Bounds native asynchronous work without putting nonblocking commands onto workers.
 * Pending reservations remain until the native stage finishes, even if callers cancel.
 */
public final class AsyncAdmission {
    private final int capacity;
    private final Runnable cleanup;
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private int pending;
    private boolean closing;
    private boolean cleanupStarted;

    /** Cleanup must release the native client and registry reservation exactly once. */
    public AsyncAdmission(int capacity, Runnable cleanup) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    /** Runs only the short command-submission callback on the caller's thread. */
    public <T> CompletionStage<T> submit(Supplier<CompletionStage<T>> operation) {
        synchronized (this) {
            if (closing) {
                return CompletableFuture.failedFuture(new IllegalStateException("Storage is closing"));
            }
            if (pending >= capacity) {
                return CompletableFuture.failedFuture(new DatabaseOverloadedException("Native request limit reached"));
            }
            pending++;
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            Objects.requireNonNull(operation.get(), "operation stage").whenComplete((value, failure) -> {
                release();
                if (failure == null) {
                    result.complete(value);
                } else {
                    result.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) {
            release();
            result.completeExceptionally(failure);
        }
        return result;
    }

    private synchronized void release() {
        pending--;
        startCleanupIfDrained();
    }

    /** Stops admission and closes resources after all native stages have settled. */
    public synchronized CompletionStage<Void> close() {
        closing = true;
        startCleanupIfDrained();
        return closed.minimalCompletionStage();
    }

    private void startCleanupIfDrained() {
        if (!closing || pending != 0 || cleanupStarted) {
            return;
        }
        cleanupStarted = true;
        // Native shutdown can block; never run it on a Netty event loop or server thread.
        Thread thread = new Thread(() -> {
            try {
                cleanup.run();
                closed.complete(null);
            } catch (Throwable failure) {
                closed.completeExceptionally(failure);
            }
        }, "DataVault-native-cleanup");
        thread.setDaemon(true);
        thread.start();
    }
}
