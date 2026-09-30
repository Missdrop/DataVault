package cn.missdrop.datavault.benchmark;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Closed-loop end-to-end test: one outstanding operation for every path.
 * Rotates order across six rounds and excludes two warm-up rounds from results.
 */
final class ComparisonRunner {
    static final int WARMUP = 2;
    static final int ROUNDS = 6;

    static void compare(String backend, Workload workload, List<BenchmarkPath> paths) throws Exception {
        Map<String, Measurement> results = new LinkedHashMap<>();
        paths.forEach(path -> results.put(path.name(), new Measurement()));
        for (int round = -WARMUP; round < ROUNDS; round++) {
            for (int offset = 0; offset < paths.size(); offset++) {
                BenchmarkPath path = paths.get(Math.floorMod(round + offset, paths.size()));
                long[] samples = new long[workload.iterations];
                long start = System.nanoTime();
                for (int i = 0; i < samples.length; i++) {
                    long submitted = System.nanoTime();
                    int result = path.run(workload.operation, workload.transaction);
                    samples[i] = System.nanoTime() - submitted;
                    if (workload.writes > 0 && result != workload.writes) {
                        throw new AssertionError("Incorrect affected row count");
                    }
                }
                long elapsed = System.nanoTime() - start;
                if (round >= 0) {
                    results.get(path.name()).add(elapsed, samples);
                }
            }
        }
        double baseline = results.get("direct-async").throughput();
        results.forEach((name, result) -> System.out.printf(
                "RESULT,%s,%s,%s,%.1f,%.1f,%.1f,%.4f%n", backend, workload.name, name,
                result.throughput(), result.percentile(0.50), result.percentile(0.95),
                result.throughput() / baseline));
    }
}
