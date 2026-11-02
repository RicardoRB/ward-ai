package dev.ward.domain.init;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A catalog row: one tool version and the runtime JDKs and build tool versions it runs on.
 *
 * @param tool the plugin artifact, Gradle plugin id, or engine name
 * @param version the version init pins
 * @param minimumJdk the lowest runtime JDK the version starts on
 * @param maximumJdk the highest runtime JDK whose class files the version reads
 * @param minimumBuildTool the lowest Maven or Gradle version the version supports
 * @param label why the row exists, for diagnostics
 */
public record ManagedPluginCandidate(
    String tool,
    String version,
    int minimumJdk,
    int maximumJdk,
    String minimumBuildTool,
    String label) {
  /**
   * Validates the row.
   *
   * @throws IllegalArgumentException if a required value is blank or the JDK range is invalid
   */
  public ManagedPluginCandidate {
    requireText(tool, "Tool is required");
    requireText(version, "Tool version is required");
    if (minimumJdk < 1 || maximumJdk < minimumJdk) {
      throw new IllegalArgumentException("Invalid JDK compatibility range");
    }
    requireText(minimumBuildTool, "Minimum build tool version is required");
    requireText(label, "Label is required");
  }

  /** Groups catalog {@code rows} by tool, keeping their order. */
  public static Map<String, List<ManagedPluginCandidate>> byTool(
      final List<ManagedPluginCandidate> rows) {
    return Map.copyOf(
        rows.stream()
            .collect(
                Collectors.groupingBy(
                    ManagedPluginCandidate::tool, LinkedHashMap::new, Collectors.toList())));
  }

  /** Returns whether {@code jdk} is inside this row's JDK range. */
  public boolean runsOn(final int jdk) {
    return jdk >= minimumJdk && jdk <= maximumJdk;
  }

  private static void requireText(final String value, final String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
