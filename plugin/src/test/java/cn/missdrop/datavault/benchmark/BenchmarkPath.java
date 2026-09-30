package cn.missdrop.datavault.benchmark;

import cn.missdrop.datavault.api.SqlOperation;

/** One execution path under comparison, returning only after its operation completes. */
interface BenchmarkPath {
    String name();
    <T> T run(SqlOperation<T> operation, boolean transaction) throws Exception;
}
