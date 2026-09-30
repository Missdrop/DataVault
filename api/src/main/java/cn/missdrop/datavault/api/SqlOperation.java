package cn.missdrop.datavault.api;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC work executed on a database worker. Close statements and result sets,
 * but never close or retain the borrowed connection. Return detached values.
 * Do not wait for nested work submitted to the same database.
 * @param <T> detached result type
 */
@FunctionalInterface
public interface SqlOperation<T> {
    T execute(Connection connection) throws SQLException;
}
