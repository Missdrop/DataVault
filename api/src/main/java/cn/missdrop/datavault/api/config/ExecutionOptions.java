package cn.missdrop.datavault.api.config;

/** Per-owner bounded admission. Overflow must fail without caller-thread execution. */
public final class ExecutionOptions {
    private final int workers;
    private final int queueCapacity;

    /**
     * Validates and stores immutable settings before resources are allocated.
     * @param workers positive worker count
     * @param queueCapacity positive waiting-task limit
     * @throws IllegalArgumentException if settings exceed their supported bounds
     */
    public ExecutionOptions(int workers, int queueCapacity) {
        if (workers < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("Workers and queue capacity must be positive");
        }
        this.workers = workers;
        this.queueCapacity = queueCapacity;
    }

    /**
     * Returns maximum concurrent database tasks for this owner.
     * @return maximum concurrent database tasks for this owner
     */
    public int workers() {
        return workers;
    }
    /**
     * Returns maximum waiting tasks, excluding currently running tasks.
     * @return maximum waiting tasks, excluding currently running tasks
     */
    public int queueCapacity() {
        return queueCapacity;
    }
}
