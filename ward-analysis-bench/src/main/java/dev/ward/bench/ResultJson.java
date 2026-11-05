package dev.ward.bench;

import java.time.Instant;
import java.util.AbstractMap;
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
  private static final String INDENT = "  ";
  private static final char SPACE = ' ';

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
    return toJson(root);
  }

  /** Renders a map of maps, lists, strings, numbers and nulls as one JSON document. */
  static String toJson(final Map<String, Object> root) {
    final StringBuilder json = new StringBuilder();
    write(json, root, 0);
    return json.append('\n').toString();
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

  private static void write(final StringBuilder json, final Object value, final int depth) {
    if (value instanceof Map<?, ?> map) {
      writeContainer(
          json,
          map.entrySet().stream()
              .map(
                  e ->
                      (Map.Entry<String, Object>)
                          new AbstractMap.SimpleImmutableEntry<>(
                              e.getKey().toString(), e.getValue()))
              .toList(),
          depth,
          '{',
          '}');
    } else if (value instanceof List<?> list) {
      writeList(json, list, depth);
    } else {
      writeScalar(json, value);
    }
  }

  private static void writeContainer(
      final StringBuilder json,
      final List<Map.Entry<String, Object>> entries,
      final int depth,
      final char open,
      final char close) {
    json.append(open);
    String separator = "";
    for (final Map.Entry<String, Object> entry : entries) {
      json.append(separator).append('\n').append(INDENT.repeat(depth + 1));
      writeString(json, entry.getKey());
      json.append(": ");
      write(json, entry.getValue(), depth + 1);
      separator = ",";
    }
    if (!entries.isEmpty()) {
      json.append('\n').append(INDENT.repeat(depth));
    }
    json.append(close);
  }

  private static void writeList(final StringBuilder json, final List<?> list, final int depth) {
    json.append('[');
    String separator = "";
    for (final Object item : list) {
      json.append(separator).append('\n').append(INDENT.repeat(depth + 1));
      write(json, item, depth + 1);
      separator = ",";
    }
    if (!list.isEmpty()) {
      json.append('\n').append(INDENT.repeat(depth));
    }
    json.append(']');
  }

  private static void writeScalar(final StringBuilder json, final Object value) {
    if (value == null) {
      json.append("null");
    } else if (value instanceof String text) {
      writeString(json, text);
    } else if (value instanceof Double number) {
      json.append(number.isNaN() || number.isInfinite() ? "null" : number.toString());
    } else {
      json.append(value);
    }
  }

  private static void writeString(final StringBuilder json, final String text) {
    json.append('"');
    for (final char c : text.toCharArray()) {
      switch (c) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '\n' -> json.append("\\n");
        case '\r' -> json.append("\\r");
        case '\t' -> json.append("\\t");
        default -> {
          if (c < SPACE) {
            json.append(String.format("\\u%04x", (int) c));
          } else {
            json.append(c);
          }
        }
      }
    }
    json.append('"');
  }
}
