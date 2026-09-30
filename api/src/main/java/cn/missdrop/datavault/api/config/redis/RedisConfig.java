package cn.missdrop.datavault.api.config.redis;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.redis.RedisStorage;
import cn.missdrop.datavault.api.config.*;
import java.time.Duration;
import java.util.Objects;

/** Dedicated Redis connection; pipeline/admission limits are per owner. */
public final class RedisConfig implements StorageConfig<RedisStorage> {
    private final String uri;
    private final Duration timeout;
    private final ExecutionOptions execution;

    /** URI credentials remain private; timeout bounds native commands, not arbitrary callbacks.
     * @param uri connection URI, which may contain credentials
     * @param timeout native command deadline
     * @param maximumPending maximum pending native operations
     */
    public RedisConfig(String uri, Duration timeout, int maximumPending) {
        this.uri = Objects.requireNonNull(uri, "uri");
        if (!uri.startsWith("redis://") && !uri.startsWith("rediss://")) {
            throw new IllegalArgumentException("Expected Redis URI");
        }
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Command timeout must be positive and at most one day");
        }
        this.execution = new ExecutionOptions(1, maximumPending);
    }

    /**
     * Returns dedicated connection with three-second timeout and 256 pending operations.
     * @return dedicated connection with three-second timeout and 256 pending operations
     * @param uri connection URI, which may contain credentials
     */
    public static RedisConfig of(String uri) {
        return new RedisConfig(uri, Duration.ofSeconds(3), 256);
    }

    /**
     * Returns sensitive Redis URI.
     * @return sensitive Redis URI
     */
    public String uri() {
        return uri;
    }

    /**
     * Returns native command timeout.
     * @return native command timeout
     */
    public Duration timeout() {
        return timeout;
    }

    @Override
    public DatabaseType type() {
        return DatabaseType.REDIS;
    }

    @Override
    public ExecutionOptions execution() {
        return execution;
    }

    @Override
    public int connectionBudget() {
        return 1;
    }

    @Override
    public Class<RedisStorage> handleType() {
        return RedisStorage.class;
    }
}
