package dev.ward.bench;

import java.util.List;

/** Median, spread and percentiles of repeated measurements. */
record Statistics(
    int count, double min, double max, double median, double spread, double p50, double p95) {

  private static final int P50 = 50;
  private static final int P95 = 95;

  /** Spread is {@code (max - min) / median}, the run-to-run variance a comparison must exceed. */
  static Statistics of(final List<Double> samples) {
    if (samples.isEmpty()) {
      throw new IllegalArgumentException("At least one sample is required");
    }
    final double[] sorted = samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    final double min = sorted[0];
    final double max = sorted[sorted.length - 1];
    final double median = median(sorted);
    final double spread = median == 0 ? 0.0 : (max - min) / median;
    return new Statistics(
        sorted.length,
        min,
        max,
        median,
        spread,
        nearestRank(sorted, P50),
        nearestRank(sorted, P95));
  }

  private static double median(final double... sorted) {
    final int middle = sorted.length / 2;
    if (sorted.length % 2 == 0) {
      return (sorted[middle - 1] + sorted[middle]) / 2.0;
    }
    return sorted[middle];
  }

  private static double nearestRank(final double[] sorted, final int percentile) {
    final int rank = (int) Math.ceil(percentile / 100.0 * sorted.length);
    return sorted[Math.max(rank, 1) - 1];
  }
}
