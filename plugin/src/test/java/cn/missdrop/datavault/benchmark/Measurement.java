package cn.missdrop.datavault.benchmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Aggregates repeated observations; medians reduce the influence of one noisy round. */
final class Measurement {
    private final List<Double> throughputs = new ArrayList<>();
    private final List<Long> latencies = new ArrayList<>();

    void add(long elapsed, long[] samples) {
        throughputs.add(samples.length * 1_000_000_000.0 / elapsed);
        for (long sample : samples) {
            latencies.add(sample);
        }
    }

    double throughput() {
        double[] sorted = throughputs.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2;
    }

    double percentile(double fraction) {
        long[] sorted = latencies.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(sorted);
        return sorted[(int) Math.ceil(sorted.length * fraction) - 1] / 1000.0;
    }
}
