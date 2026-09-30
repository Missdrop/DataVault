package cn.missdrop.datavault.benchmark;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/** Same rotated, warmed-up methodology as JDBC, but no conversion to JDBC-shaped callbacks. */
final class NativeComparison {
    static void compare(String backend, String workload, int iterations,
                        Map<String, Callable<Integer>> paths) throws Exception {
        var names = paths.keySet().toArray(new String[0]);
        Map<String, Measurement> results = new LinkedHashMap<>();
        paths.forEach((name, operation) -> results.put(name, new Measurement()));
        for (int round = -ComparisonRunner.WARMUP; round < ComparisonRunner.ROUNDS; round++) {
            for (int offset = 0; offset < names.length; offset++) {
                String name = names[Math.floorMod(round + offset, names.length)];
                var operation = paths.get(name);
                long[] samples = new long[iterations];
                long start = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    long submitted = System.nanoTime();
                    if (operation.call() < 0) {
                        throw new AssertionError("Incorrect native result");
                    }
                    samples[i] = System.nanoTime() - submitted;
                }
                long elapsed = System.nanoTime() - start;
                if (round >= 0) {
                    results.get(name).add(elapsed, samples);
                }
            }
        }
        double baseline = results.get("direct-async").throughput();
        results.forEach((name, result) -> System.out.printf(
                "RESULT,%s,%s,%s,%.1f,%.1f,%.1f,%.4f%n", backend, workload, name,
                result.throughput(), result.percentile(0.50), result.percentile(0.95), result.throughput() / baseline));
    }

    private NativeComparison() { }
}
