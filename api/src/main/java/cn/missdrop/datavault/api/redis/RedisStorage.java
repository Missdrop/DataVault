package cn.missdrop.datavault.api.redis;

import cn.missdrop.datavault.api.Storage;
import java.util.concurrent.CompletionStage;

/** Dedicated Redis connection using native asynchronous commands and bounded admission. */
public interface RedisStorage extends Storage {
    /**
     * Initiates nonblocking commands without a second thread-pool handoff.
     * The callback itself must be short and nonblocking. Do not use connection
     * state changes, MULTI/EXEC, blocking commands, or disable auto-flush on the
     * shared connection. Compose returned command stages to keep work in scope.
     * @param operation native asynchronous Redis work
     * @param <T> result type
     * @return stage completing after all work represented by the returned stage
     */
    <T> CompletionStage<T> execute(RedisOperation<T> operation);
}
