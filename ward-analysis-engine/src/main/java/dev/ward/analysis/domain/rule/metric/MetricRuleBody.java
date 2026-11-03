package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.port.AnalysisFailedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the body of a metric rule. A body is {@code key=value} pairs separated by semicolons, for
 * example {@code measure=lines; node=funcdef; max=60; message=...}. The {@code node} value is a
 * comma-separated list of kinds, each optionally followed by {@code :style}.
 */
final class MetricRuleBody {
  private static final String PAIR_SEPARATOR = ";";
  private static final String VALUE_SEPARATOR = ",";
  private static final int KEY_AND_VALUE = 2;

  private final Map<String, String> pairs = new HashMap<>();

  private MetricRuleBody(final String body) {
    for (final String pair : body.split(PAIR_SEPARATOR)) {
      final String[] parts = pair.trim().split("=", KEY_AND_VALUE);
      if (parts.length != KEY_AND_VALUE) {
        throw new AnalysisFailedException(
            "metric rule body has a part without '=': " + pair.trim());
      }
      pairs.put(parts[0].trim(), parts[1].trim());
    }
  }

  /** Parses {@code body} into a metric rule, or fails if the body is not a valid metric rule. */
  static MetricRule parse(final String body) {
    return new MetricRuleBody(body).rule();
  }

  private MetricRule rule() {
    final Measurement measurement = measurement();
    return new MetricRule(
        measurement,
        targets(),
        pairs.containsKey("max") ? Integer.parseInt(required("max")) : 0,
        required("message"),
        keys());
  }

  private Measurement measurement() {
    return Measurement.byKey(required("measure"))
        .orElseThrow(() -> new AnalysisFailedException("metric rule names an unknown measurement"));
  }

  private List<MetricRule.Target> targets() {
    final List<MetricRule.Target> targets = new ArrayList<>();
    for (final String entry : required("node").split(VALUE_SEPARATOR)) {
      targets.add(target(entry.trim()));
    }
    return List.copyOf(targets);
  }

  private static MetricRule.Target target(final String entry) {
    final String[] parts = entry.split(":", KEY_AND_VALUE);
    final NameStyle style = parts.length == KEY_AND_VALUE ? style(parts[1]) : null;
    return new MetricRule.Target(parts[0], style);
  }

  private static NameStyle style(final String key) {
    return NameStyle.byKey(key)
        .orElseThrow(() -> new AnalysisFailedException("metric rule names an unknown style"));
  }

  private MetricRule.MetricRuleKeys keys() {
    return new MetricRule.MetricRuleKeys(
        pairs.getOrDefault("name", ""),
        path(pairs.getOrDefault("path", "")),
        set(pairs.get("receivers")),
        set(pairs.get("nested")),
        set(pairs.get("decisions")));
  }

  private static List<String> path(final String value) {
    return value.isEmpty() ? List.of() : Arrays.asList(value.split("/"));
  }

  private static Set<String> set(final String value) {
    if (value == null || value.isEmpty()) {
      return Set.of();
    }
    return Arrays.stream(value.split(VALUE_SEPARATOR))
        .map(String::trim)
        .collect(Collectors.toUnmodifiableSet());
  }

  private String required(final String key) {
    final String value = pairs.get(key);
    if (value == null) {
      throw new AnalysisFailedException("metric rule is missing '" + key + "'");
    }
    return value;
  }
}
