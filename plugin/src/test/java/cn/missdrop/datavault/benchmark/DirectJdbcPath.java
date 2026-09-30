package cn.missdrop.datavault.benchmark;

import cn.missdrop.datavault.api.SqlOperation;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.concurrent.*;

/**
 * Independent plugin-style JDBC baseline. The asynchronous variant uses its own
 * bounded executor and future, without calling DataVault's execution code.
 */
final class DirectJdbcPath implements BenchmarkPath, AutoCloseable {
    private final HikariDataSource pool;
    private final ExecutorService worker;

    DirectJdbcPath(HikariDataSource pool, boolean asynchronous) {
        this.pool = pool;
        this.worker = asynchronous ? new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256), new ThreadPoolExecutor.AbortPolicy()) : null;
    }

    @Override
    public String name() {
        return worker == null ? "direct-sync" : "direct-async";
    }

    @Override
    public <T> T run(SqlOperation<T> operation, boolean transaction) throws Exception {
        if (worker == null) {
            return invoke(operation, transaction);
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                result.complete(invoke(operation, transaction));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    /** Transaction implementation is independent but follows the same JDBC semantics. */
    private <T> T invoke(SqlOperation<T> operation, boolean transaction) throws Exception {
        try (Connection connection = pool.getConnection()) {
            if (!transaction) {
                return operation.execute(connection);
            }
            connection.setAutoCommit(false);
            try {
                T result = operation.execute(connection);
                connection.commit();
                return result;
            } catch (Exception | Error failure) {
                try {
                    connection.rollback();
                } catch (Exception rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    @Override
    public void close() throws InterruptedException {
        if (worker != null) {
            worker.shutdown();
            if (!worker.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Baseline executor did not terminate");
            }
        }
    }
}
