package cn.missdrop.datavault.benchmark;

import cn.missdrop.datavault.api.Database;
import cn.missdrop.datavault.api.SqlOperation;
import java.util.concurrent.TimeUnit;

/** Measures the public DataVault operation, including submission and completion. */
final class VaultPath implements BenchmarkPath {
    private final Database database;

    VaultPath(Database database) {
        this.database = database;
    }

    @Override
    public String name() {
        return "datavault";
    }

    @Override
    public <T> T run(SqlOperation<T> operation, boolean transaction) throws Exception {
        return (transaction ? database.transaction(operation) : database.execute(operation))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
