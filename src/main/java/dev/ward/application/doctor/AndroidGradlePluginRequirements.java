package dev.ward.application.doctor;

import java.util.Map;
import java.util.Optional;

/**
 * What an Android Gradle Plugin version needs from the Gradle and JDK that run it: the JDK per
 * plugin major, and the Gradle minimum of the minors the plugin's release notes name.
 */
final class AndroidGradlePluginRequirements {
  private static final int JDK_SEVENTEEN = 17;
  private static final int JDK_ELEVEN = 11;
  private static final int PLUGIN_EIGHT = 8;
  private static final int PLUGIN_SEVEN = 7;
  private static final Map<String, String> GRADLE_MINIMUM =
      Map.of(
          "8.3", "8.4",
          "8.4", "8.6",
          "8.9", "8.11.1",
          "8.10", "8.11.1",
          "8.11", "8.13",
          "8.12", "8.13");

  private AndroidGradlePluginRequirements() {}

  /** Returns the oldest JDK that runs the plugin, or none when the major is not known. */
  static Optional<Integer> minimumJdk(final String plugin) {
    final int major = major(plugin);
    if (major >= PLUGIN_EIGHT) {
      return Optional.of(JDK_SEVENTEEN);
    }
    return major == PLUGIN_SEVEN ? Optional.of(JDK_ELEVEN) : Optional.empty();
  }

  /** Returns the oldest Gradle that runs the plugin's minor, or none when it is not listed. */
  static Optional<String> minimumGradle(final String plugin) {
    final String[] parts = plugin.split("\\.");
    return parts.length < 2
        ? Optional.empty()
        : Optional.ofNullable(GRADLE_MINIMUM.get(parts[0] + "." + parts[1]));
  }

  /** Returns whether {@code version} is older than {@code minimum}, comparing numeric parts. */
  static boolean older(final String version, final String minimum) {
    final String[] have = version.split("\\.");
    final String[] need = minimum.split("\\.");
    for (int i = 0; i < Math.max(have.length, need.length); i++) {
      final int difference = part(have, i) - part(need, i);
      if (difference != 0) {
        return difference < 0;
      }
    }
    return false;
  }

  private static int major(final String plugin) {
    return part(plugin.split("\\."), 0);
  }

  /** The numeric value of the part at {@code index}; a missing or non-numeric part is zero. */
  private static int part(final String[] parts, final int index) {
    if (index >= parts.length) {
      return 0;
    }
    final String digits = parts[index].replaceAll("\\D.*$", "");
    return digits.isEmpty() ? 0 : Integer.parseInt(digits);
  }
}
