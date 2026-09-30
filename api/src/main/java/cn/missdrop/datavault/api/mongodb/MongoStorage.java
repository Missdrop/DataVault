package cn.missdrop.datavault.api.mongodb;

import cn.missdrop.datavault.api.Storage;
import java.util.concurrent.CompletionStage;

/** Native MongoDB handle. No JDBC serialization or document copying is added. */
public interface MongoStorage extends Storage {
    /**
     * Runs native driver work on this owner's bounded workers.
     * Close cursors inside the callback and return detached values. Do not retain
     * the borrowed driver handle after closure or block waiting for nested work.
     * @param operation native MongoDB work
     * @param <T> detached result type
     * @return callback result or failure
     */
    <T> CompletionStage<T> execute(MongoOperation<T> operation);
}
