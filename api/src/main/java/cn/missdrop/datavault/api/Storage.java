package cn.missdrop.datavault.api;

import java.util.concurrent.CompletionStage;

/** Lifecycle shared by JDBC and native stores; no SQL semantics are implied. */
public interface Storage {
    /**
     * Returns stable owner identity.
     * @return stable owner identity
     */
    PluginId owner();

    /**
     * Returns concrete backend type.
     * @return concrete backend type
     */
    DatabaseType type();

    /**
     * Returns completion after accepted operations drain and resources are released.
     * @return completion after accepted operations drain and resources are released
     */
    CompletionStage<Void> close();
}
