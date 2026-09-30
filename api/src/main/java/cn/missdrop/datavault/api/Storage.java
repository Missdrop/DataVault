package cn.missdrop.datavault.api;

import java.util.concurrent.CompletionStage;

/** Lifecycle shared by JDBC and native stores; no SQL semantics are implied. */
public interface Storage {
    /** @return stable owner identity */
    PluginId owner();

    /** @return concrete backend type */
    DatabaseType type();

    /** @return completion after accepted operations drain and resources are released */
    CompletionStage<Void> close();
}
