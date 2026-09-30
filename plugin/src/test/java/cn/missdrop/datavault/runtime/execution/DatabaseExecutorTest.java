package cn.missdrop.datavault.runtime.execution;

import cn.missdrop.datavault.api.config.ExecutionOptions;
import cn.missdrop.datavault.api.exception.DatabaseOverloadedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class DatabaseExecutorTest {
    @Test
    public void overloadIsLocalAndShutdownDrainsAcceptedWork() throws Exception {
        DatabaseExecutor first = new DatabaseExecutor("first", new ExecutionOptions(1, 1), () -> {});
        DatabaseExecutor second = new DatabaseExecutor("second", new ExecutionOptions(1, 1), () -> {});
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var running = first.submit(() -> {
                started.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for release");
                }
                return 1;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var queued = first.submit(() -> 2);
            var rejected = first.submit(() -> 3).toCompletableFuture();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> rejected.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof DatabaseOverloadedException);
            assertEquals(4, second.submit(() -> 4).toCompletableFuture().get(5, TimeUnit.SECONDS).intValue());
            var closing = first.close().toCompletableFuture();
            assertFalse(closing.isDone());
            assertThrows(ExecutionException.class,
                    () -> first.submit(() -> 5).toCompletableFuture().get(5, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(1, running.toCompletableFuture().get(5, TimeUnit.SECONDS).intValue());
            assertEquals(2, queued.toCompletableFuture().get(5, TimeUnit.SECONDS).intValue());
            closing.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            first.close().toCompletableFuture().get(5, TimeUnit.SECONDS);
            second.close().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }
}
