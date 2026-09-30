package cn.missdrop.datavault.api.config;

import java.time.Duration;
import java.util.Objects;

/** Per-owner pool options without exposing a pool implementation. */
public final class PoolOptions {
    private final int maximumSize;
    private final int minimumIdle;
    private final Duration acquisitionTimeout;

    /**
     * Validates and stores immutable settings before resources are allocated.
     * @param maximumSize positive maximum connection count
     * @param minimumIdle idle connections, between zero and maximumSize
     * @param acquisitionTimeout connection borrowing timeout, at least 250 milliseconds
     * @throws IllegalArgumentException if settings exceed their supported bounds
     */
    public PoolOptions(int maximumSize, int minimumIdle, Duration acquisitionTimeout) {
        if (maximumSize < 1 || minimumIdle < 0 || minimumIdle > maximumSize) {
            throw new IllegalArgumentException("Invalid pool size bounds");
        }
        Objects.requireNonNull(acquisitionTimeout, "acquisitionTimeout");
        if (acquisitionTimeout.compareTo(Duration.ofMillis(250)) < 0
                || acquisitionTimeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Acquisition timeout must be 250..2147483647 milliseconds");
        }
        this.maximumSize = maximumSize;
        this.minimumIdle = minimumIdle;
        this.acquisitionTimeout = acquisitionTimeout;
    }

    /**
     * Creates conservative defaults: three connections, no required idle connections.
     * @return validated immutable configuration
     */
    public static PoolOptions defaults() {
        return new PoolOptions(3, 0, Duration.ofSeconds(3));
    }

    /**
     * Returns maximum connections reserved for this owner.
     * @return maximum connections reserved for this owner
     */
    public int maximumSize() {
        return maximumSize;
    }
    /**
     * Returns minimum idle connections maintained by the pool.
     * @return minimum idle connections maintained by the pool
     */
    public int minimumIdle() {
        return minimumIdle;
    }

    /**
     * Returns maximum time to borrow a connection; this is not a query timeout.
     * @return maximum time to borrow a connection; this is not a query timeout
     */
    public Duration acquisitionTimeout() {
        return acquisitionTimeout;
    }
}
