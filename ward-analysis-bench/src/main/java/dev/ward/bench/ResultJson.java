package dev.ward.bench;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link BenchmarkResult} as a speed document of schema version 2. Version 1 documents
 * have no {@code mode} key and are speed documents too. The output holds no environment variables
 * and no captured process output.
 */
final class ResultJson {

  static final int SCHEMA_VERSION = 2;
  static final String SPEED_MODE = "speed";

  private ResultJson() {}

  static String render(final BenchmarkResult result, final Instant generatedAt) {
    final Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("mode", SPEED_MODE);
    root.put("generatedAt", generatedAt.toString());
    root.put("completed", result.completed());
    root.put(
        "host", Map.of("os", System.getProperty("os.name"), "arch", System.getProperty("os.arch")));
    root.put(
        "binary",
        Map.of("sizeBytes", result.binary().sizeBytes(), "sha256", result.binary().sha256()));
    root.put("repeats", Map.of("full", result.fullRepeats(), "edit", result.editRepeats()));
    root.put("manifest", manifest(result));
    root.put("entries", entries(result));
    return JsonDocument.toJson(root);
  }

  private static List<Object> manifest(final BenchmarkResult result) {
    return result.entries().stream()
        .map(
            entry ->
                (Object)
                    Map.of(
                        "language", entry.entry().language(),
                        "name", entry.entry().name(),
                        "url", entry.entry().url(),
                        "commit", entry.entry().commit()))
        .toList();
  }

  private static List<Object> entries(final BenchmarkResult result) {
    return result.entries().stream()
        .map(
            entry ->
                (Object)
                    Map.of(
                        "name", entry.entry().name(),
                        "jobs", entry.jobs().stream().map(ResultJson::job).toList()))
        .toList();
  }

  private static Map<String, Object> job(final BenchmarkResult.JobResult job) {
    final Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", job.name());
    row.put("command", job.command());
    row.put("completed", job.completed());
    row.put("exitCodes", job.exitCodes());
    row.put("coldMillis", stats(job.cold()));
    row.put("warmMillis", stats(job.warm()));
    row.put("peakRssKb", stats(job.peakRssKb()));
    if (job.edit() != null) {
      row.put("editMillis", stats(job.edit()));
    }
    return row;
  }

  private static Map<String, Object> stats(final Statistics stats) {
    final Map<String, Object> row = new LinkedHashMap<>();
    row.put("count", stats.count());
    row.put("min", stats.min());
    row.put("max", stats.max());
    row.put("median", stats.median());
    row.put("spread", stats.spread());
    row.put("p50", stats.p50());
    row.put("p95", stats.p95());
    return row;
  }
}
