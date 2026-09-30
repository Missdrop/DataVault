package cn.missdrop.datavault.runtime.registry;

import cn.missdrop.datavault.api.config.StorageConfig;

/** Registry-lock guarded reservations, including databases still opening. */
public final class ResourceBudget {
    private final int connectionLimit;
    private final int workerLimit;
    private final long queueLimit;
    private int connections;
    private int workers;
    private long queues;

    /** Queue budget counts task slots, not retained object bytes. Callers must bound payload sizes. */
    public ResourceBudget(int connectionLimit, int workerLimit, long queueLimit) {
        if (connectionLimit < 1 || workerLimit < 1 || queueLimit < 1) {
            throw new IllegalArgumentException("Resource limits must be positive");
        }
        this.connectionLimit = connectionLimit;
        this.workerLimit = workerLimit;
        this.queueLimit = queueLimit;
    }

    /** Checks all dimensions before updating any counter, so rejection is atomic. */
    public void reserve(StorageConfig<?> config) {
        int requested = connections(config);
        if (requested > connectionLimit - connections
                || config.execution().workers() > workerLimit - workers
                || config.execution().queueCapacity() > queueLimit - queues) {
            throw new IllegalStateException("Global database resource budget exceeded");
        }
        connections += requested;
        workers += config.execution().workers();
        queues += config.execution().queueCapacity();
    }

    /** Called once after removal of the matching registration, including failed opens. */
    public void release(StorageConfig<?> config) {
        connections -= connections(config);
        workers -= config.execution().workers();
        queues -= config.execution().queueCapacity();
    }

    private int connections(StorageConfig<?> config) {
        return config.connectionBudget();
    }
}
