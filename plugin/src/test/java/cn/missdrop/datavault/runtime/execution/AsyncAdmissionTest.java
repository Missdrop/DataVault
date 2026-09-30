package cn.missdrop.datavault.runtime.execution;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

/** Cancellation must not release an in-flight native command or prematurely close its connection. */
public class AsyncAdmissionTest {
    @Test public void cancellationRetainsReservationAndCleanupRunsOnce() throws Exception {
        var cleanup = new AtomicInteger();
        var admission = new AsyncAdmission(1, cleanup::incrementAndGet);
        var nativeResult = new CompletableFuture<String>();
        var result = admission.submit(() -> nativeResult).toCompletableFuture();
        result.cancel(false);
        assertThrows(ExecutionException.class, () -> admission.submit(() -> CompletableFuture.completedFuture("rejected"))
                .toCompletableFuture().get());
        var closing = admission.close().toCompletableFuture();
        assertFalse(closing.isDone());
        nativeResult.complete("done");
        closing.get(5, TimeUnit.SECONDS);
        admission.close().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(1, cleanup.get());
    }

    @Test public void callbackFailureReleasesAdmission() throws Exception {
        var admission = new AsyncAdmission(1, () -> { });
        assertThrows(ExecutionException.class, () -> admission.submit(() -> { throw new IllegalArgumentException("intentional"); })
                .toCompletableFuture().get());
        assertEquals("ok", admission.submit(() -> CompletableFuture.completedFuture("ok")).toCompletableFuture().get());
        admission.close().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
