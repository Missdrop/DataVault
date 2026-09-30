package cn.missdrop.datavault.runtime.execution;

import cn.missdrop.datavault.api.config.ExecutionOptions;
import cn.missdrop.datavault.api.exception.DatabaseOverloadedException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-owner admission and workers. Rejection never executes work on the caller.
 * Completion handlers must remain short or use an explicit external executor.
 */
public final class DatabaseExecutor {
    private final ThreadPoolExecutor workers;
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();

    /** Allocates lazy workers; the cleanup callback runs after accepted tasks have drained. */
    public DatabaseExecutor(String owner, ExecutionOptions options, Runnable onTermination) {
        AtomicInteger sequence = new AtomicInteger();
        workers = new ThreadPoolExecutor(options.workers(), options.workers(),
                30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(options.queueCapacity()),
                task -> {
                    Thread thread = new Thread(task, "DataVault-" + owner + "-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy()) {
            @Override
            protected void terminated() {
                // Keep resource cleanup off the thread requesting shutdown.
                Thread cleanup = new Thread(() -> {
                    try {
                        onTermination.run();
                        terminated.complete(null);
                    } catch (Throwable failure) {
                        terminated.completeExceptionally(failure);
                    }
                }, "DataVault-" + owner + "-cleanup");
                cleanup.setDaemon(true);
                cleanup.start();
            }
        };
        workers.allowCoreThreadTimeOut(true);
    }

    /** Returns failures through the stage, including local overload and closure rejection. */
    public <T> CompletionStage<T> submit(CheckedTask<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            workers.execute(() -> {
                if (result.isCancelled()) {
                    // Only not-yet-started work is skipped; interrupting JDBC is driver-specific.
                    return;
                }
                try {
                    result.complete(task.run());
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            RuntimeException rejection = workers.isShutdown()
                    ? new IllegalStateException("Database is closing")
                    : new DatabaseOverloadedException("Database queue is full");
            result.completeExceptionally(rejection);
        }
        return result;
    }

    /** Stops admission immediately; repeated calls observe the same termination result. */
    public CompletionStage<Void> close() {
        workers.shutdown();
        return terminated.minimalCompletionStage();
    }

    /** Observes cleanup without initiating it, allowing registry lifecycle coordination. */
    public CompletionStage<Void> whenClosed() {
        return terminated.minimalCompletionStage();
    }

    @FunctionalInterface
    /** Internal work may fail with JDBC or other checked exceptions. */
    public interface CheckedTask<T> {
        T run() throws Exception;
    }
}
