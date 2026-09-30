package cn.missdrop.datavault.runtime.transaction;

import cn.missdrop.datavault.api.SqlOperation;
import java.sql.Connection;
import java.sql.SQLException;

/** Transaction boundaries; pool return handles connection state reset. */
public final class Transactions {
    private Transactions() {}

    /** Commits only after successful work; preserve the original failure if rollback also fails. */
    public static <T> T run(Connection connection, SqlOperation<T> operation) throws SQLException {
        connection.setAutoCommit(false);
        try {
            T result = operation.execute(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }
}
