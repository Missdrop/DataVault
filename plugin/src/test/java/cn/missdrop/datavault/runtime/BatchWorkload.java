package cn.missdrop.datavault.runtime;

import cn.missdrop.datavault.api.Database;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;

/** Small repeatable real-JDBC workload; elapsed times are observations, not CI thresholds. */
final class BatchWorkload {
    private BatchWorkload() {}

    static void verify(Database database, String table, int rows) throws Exception {
        long start = System.nanoTime();
        database.transaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO " + table + " (id, balance) VALUES (?, ?)")) {
                for (int i = 2; i < rows + 2; i++) {
                    statement.setInt(1, i);
                    statement.setInt(2, i);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }).toCompletableFuture().get(30, TimeUnit.SECONDS);
        long elapsed = System.nanoTime() - start;
        int count = database.execute(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                result.next();
                return result.getInt(1);
            }
        }).toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(rows + 1, count);
        System.out.printf("%s batch: %d rows, %.2f ms, %.0f rows/s%n",
                database.type(), rows, elapsed / 1_000_000.0, rows * 1_000_000_000.0 / elapsed);
    }
}
