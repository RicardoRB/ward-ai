package dev.ward.infrastructure.config;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Parses the keys of an analysis ({@code ward}) check: {@code rules} selects analysis rule ids, and
 * no other key beyond the common ones is accepted.
 */
final class TomlAnalysisParser {
  private static final String RULES = "rules";
  private static final String JAVA_CLASSPATH = "java-classpath";
  private static final String RULE_PREFIX = "ward:";
  private static final Set<String> KEYS =
      Set.of("uses", "enabled", "profiles", RULES, JAVA_CLASSPATH);

  private TomlAnalysisParser() {}

  /** Returns the selected rule ids, or an empty list when the check selects every rule. */
  static List<String> parse(final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.ANALYSIS.equals(type)) {
      return List.of();
    }
    rejectUnknownKeys(where, gate);
    return gate.contains(RULES) ? rules(where + "." + RULES, gate.getArray(RULES)) : List.of();
  }

  /**
   * Returns the project-relative classpath file that enables Java binding resolution, or {@code
   * null} when the check does not set it.
   */
  static String javaClasspath(final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.ANALYSIS.equals(type) || !gate.contains(JAVA_CLASSPATH)) {
      return null;
    }
    final String path = gate.getString(JAVA_CLASSPATH);
    if (path == null || path.isBlank()) {
      throw new WardException(
          "'" + where + "." + JAVA_CLASSPATH + "' must be the path of a classpath file");
    }
    return path;
  }

  private static void rejectUnknownKeys(final String where, final TomlTable gate) {
    for (final String key : gate.keySet()) {
      if (!KEYS.contains(key)) {
        throw new WardException(
            "'"
                + where
                + "."
                + key
                + "' is not a key of an analysis check. Supported keys: "
                + KEYS);
      }
    }
  }

  private static List<String> rules(final String key, final TomlArray array) {
    if (array == null) {
      throw new WardException("'" + key + "' must be an array of ward rule ids");
    }
    if (array.isEmpty()) {
      throw new WardException("'" + key + "' must not be empty");
    }
    final List<String> ids = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      ids.add(ruleId(key, array, index));
    }
    return ids;
  }

  private static String ruleId(final String key, final TomlArray array, final int index) {
    final String id = array.isString(index) ? array.getString(index) : null;
    if (id == null || !id.startsWith(RULE_PREFIX) || id.length() == RULE_PREFIX.length()) {
      throw new WardException(
          "'" + key + "' must contain only ward rule ids such as ward:java/method-length");
    }
    return id;
  }
}
