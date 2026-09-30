package cn.missdrop.datavault.api.exception;

/** This owner's queue is full. Retry policies belong to the caller. */
public final class DatabaseOverloadedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DatabaseOverloadedException(String message) { super(message); }
}
