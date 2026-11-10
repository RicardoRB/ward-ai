package dev.ward.bench;

/** The short human-readable summary printed after a run. */
final class Summary {

  private static final String LINE =
      "%-16s %-22s cold %8.0f ms (spread %.2f)  warm %8.0f ms (spread %.2f)"
          + "  peak RSS %9.0f KB%n";
  private static final String EDIT_LINE = "%-16s %-22s edit p50 %8.0f ms  p95 %8.0f ms%n";

  private Summary() {}

  static String render(final BenchmarkResult result) {
    final StringBuilder text = new StringBuilder();
    text.append(
        String.format(
            "Native binary %d bytes, completed=%s%n",
            result.binary().sizeBytes(), result.completed()));
    for (final BenchmarkResult.EntryResult entry : result.entries()) {
      for (final BenchmarkResult.JobResult job : entry.jobs()) {
        text.append(
            String.format(
                LINE,
                job.name(),
                entry.entry().name(),
                job.cold().median(),
                job.cold().spread(),
                job.warm().median(),
                job.warm().spread(),
                job.peakRssKb().median()));
        if (job.edit() != null) {
          text.append(
              String.format(
                  EDIT_LINE, job.name(), entry.entry().name(), job.edit().p50(), job.edit().p95()));
        }
      }
    }
    return text.toString();
  }
}
