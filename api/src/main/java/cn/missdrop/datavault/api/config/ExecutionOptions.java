package cn.missdrop.datavault.api.config;

/** Per-owner bounded admission. Overflow must fail without caller-thread execution. */
public final class ExecutionOptions {
    private final int workers;
    private final int queueCapacity;

    public ExecutionOptions(int workers, int queueCapacity) {
        if (workers < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("Workers and queue capacity must be positive");
        }
        this.workers = workers;
        this.queueCapacity = queueCapacity;
    }

    public int workers() { return workers; }
    public int queueCapacity() { return queueCapacity; }
}
