package cn.missdrop.datavault.benchmark;

import cn.missdrop.datavault.api.SqlOperation;
import java.util.Arrays;
import java.util.List;

/** Identical prepared SQL callbacks are supplied to every execution path. */
final class Workload {
    final String name;
    final int iterations;
    final boolean transaction;
    final int writes;
    final SqlOperation<Integer> operation;

    private Workload(String name, int iterations, boolean transaction,
                     int writes, SqlOperation<Integer> operation) {
        this.name = name;
        this.iterations = iterations;
        this.transaction = transaction;
        this.writes = writes;
        this.operation = operation;
    }

    static List<Workload> forTable(String table) {
        Workload read = new Workload("indexed-read", 1500, false, 0, connection -> {
            try (var query = connection.prepareStatement("SELECT balance FROM " + table + " WHERE id = ?")) {
                query.setInt(1, 1);
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) {
                        throw new IllegalStateException("Missing benchmark row");
                    }
                    return rows.getInt(1);
                }
            }
        });
        Workload update = new Workload("single-update", 400, false, 1, connection -> {
            try (var query = connection.prepareStatement("UPDATE " + table + " SET balance = balance + 1 WHERE id = ?")) {
                query.setInt(1, 1);
                return query.executeUpdate();
            }
        });
        Workload batch = new Workload("transaction-100", 60, true, 100, connection -> {
            try (var query = connection.prepareStatement("UPDATE " + table + " SET balance = balance + 1 WHERE id = ?")) {
                for (int i = 0; i < 100; i++) {
                    query.setInt(1, 1);
                    query.addBatch();
                }
                query.executeBatch();
                return 100;
            }
        });
        return Arrays.asList(read, update, batch);
    }
}
