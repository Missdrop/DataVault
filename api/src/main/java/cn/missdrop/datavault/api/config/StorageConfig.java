package cn.missdrop.datavault.api.config;

import cn.missdrop.datavault.api.DatabaseType;
import cn.missdrop.datavault.api.Storage;

/** Typed immutable configuration. Handle type prevents casting native stores to JDBC. */
public interface StorageConfig<S extends Storage> {
    /** @return backend selected by this configuration */
    DatabaseType type();

    /** @return per-owner admission limits */
    ExecutionOptions execution();

    /** @return maximum application connections reserved by this owner */
    int connectionBudget();

    /** @return public handle interface implemented by this backend */
    Class<S> handleType();
}
