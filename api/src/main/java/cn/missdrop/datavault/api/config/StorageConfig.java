package cn.missdrop.datavault.api.config;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.Storage;

/**
 * Typed immutable configuration. Handle type prevents casting native stores to JDBC.
 * @param <S> public backend handle type
 */
public interface StorageConfig<S extends Storage> {
    /**
     * Returns backend selected by this configuration.
     * @return backend selected by this configuration
     */
    DatabaseType type();

    /**
     * Returns per-owner admission limits.
     * @return per-owner admission limits
     */
    ExecutionOptions execution();

    /**
     * Returns maximum application connections reserved by this owner.
     * @return maximum application connections reserved by this owner
     */
    int connectionBudget();

    /**
     * Returns public handle interface implemented by this backend.
     * @return public handle interface implemented by this backend
     */
    Class<S> handleType();
}
