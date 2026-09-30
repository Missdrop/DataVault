package cn.missdrop.datavault.api.config;

import java.time.Duration;
import java.util.Objects;

/** Per-owner pool options without exposing a pool implementation. */
public final class PoolOptions {
    private final int maximumSize;
    private final int minimumIdle;
    private final Duration acquisitionTimeout;

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

    public static PoolOptions defaults() {
        return new PoolOptions(3, 0, Duration.ofSeconds(3));
    }

    public int maximumSize() { return maximumSize; }
    public int minimumIdle() { return minimumIdle; }

    /** Connection borrowing timeout, not a query timeout. */
    public Duration acquisitionTimeout() { return acquisitionTimeout; }
}
