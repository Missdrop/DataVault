package cn.missdrop.datavault.api.mongodb;

import com.mongodb.client.MongoDatabase;

/** Scoped synchronous MongoDB work, executed off the server thread. */
@FunctionalInterface
public interface MongoOperation<T> {
    /**
     * Executes using the native driver without intermediate data conversion.
     * @param database selected native database
     * @return detached result
     * @throws Exception if work fails
     */
    T execute(MongoDatabase database) throws Exception;
}
