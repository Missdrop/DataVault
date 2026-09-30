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
                CompletableFuture.runAsync(() -> {
                    try {
                        onTermination.run();
                        terminated.complete(null);
                    } catch (Throwable failure) {
                        terminated.completeExceptionally(failure);
                    }
                });
            }
        };
        workers.allowCoreThreadTimeOut(true);
    }

    public <T> CompletionStage<T> submit(CheckedTask<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            workers.execute(() -> {
                if (result.isCancelled()) {
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

    public CompletionStage<Void> close() {
        workers.shutdown();
        return terminated.minimalCompletionStage();
    }

    @FunctionalInterface
    public interface CheckedTask<T> {
        T run() throws Exception;
    }
}
