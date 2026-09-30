package cn.missdrop.datavault.api.redis;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.concurrent.CompletionStage;

/** Native command submission; returned stage must cover every submitted command. */
@FunctionalInterface
public interface RedisOperation<T> {
    /**
     * Submits native commands without converting keys, values, or results.
     * @param commands scoped UTF-8 string command interface
     * @return stage representing all submitted work
     */
    CompletionStage<T> execute(RedisAsyncCommands<String, String> commands);
}
