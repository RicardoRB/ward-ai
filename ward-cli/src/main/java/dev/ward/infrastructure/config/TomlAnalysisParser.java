package dev.ward.infrastructure.config;

import dev.ward.analysis.domain.rule.LayerBoundary;
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
  private static final String RULES_FILE = "rules-file";
  private static final String LAYERS = "layers";
  private static final String FROM = "from";
  private static final String FORBIDDEN = "forbidden";
  private static final Set<String> BOUNDARY_KEYS = Set.of(FROM, FORBIDDEN);
  private static final String RULE_PREFIX = "ward:";
  private static final String PATHS = "paths";
  private static final Set<String> KEYS =
      Set.of(
          "uses",
          "enabled",
          "profiles",
          RULES,
          JAVA_CLASSPATH,
          RULES_FILE,
          LAYERS,
          PATHS,
          TomlArchitectureParser.CYCLES,
          TomlArchitectureParser.INDEPENDENCE);

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

  /**
   * Returns the project-relative path of the rule configuration file, or {@code null} when the
   * check uses {@code ward-rules.toml}. The path must stay inside the project.
   */
  static String rulesFile(final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.ANALYSIS.equals(type) || !gate.contains(RULES_FILE)) {
      return null;
    }
    final String path = gate.isString(RULES_FILE) ? gate.getString(RULES_FILE) : null;
    if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..")) {
      throw new WardException(
          "'"
              + where
              + "."
              + RULES_FILE
              + "' must be a relative path inside the project, such as ward-rules.toml");
    }
    return path;
  }

  /**
   * Returns the architecture boundaries of a {@code ward} check, or an empty list when it sets
   * none. Each {@code [[checks.<name>.layers]]} table has a non-empty {@code from} and {@code
   * forbidden} list of plain directory names.
   */
  static List<LayerBoundary> layers(final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.ANALYSIS.equals(type) || !gate.contains(LAYERS)) {
      return List.of();
    }
    final TomlArray tables = gate.getArray(LAYERS);
    if (tables == null) {
      throw new WardException("'" + where + "." + LAYERS + "' must be an array of tables");
    }
    final List<LayerBoundary> boundaries = new ArrayList<>();
    for (int index = 0; index < tables.size(); index++) {
      boundaries.add(boundary(where + "." + LAYERS, tables.getTable(index)));
    }
    return List.copyOf(boundaries);
  }

  private static LayerBoundary boundary(final String key, final TomlTable table) {
    if (table == null) {
      throw new WardException("'" + key + "' must be an array of tables");
    }
    for (final String name : table.keySet()) {
      if (!BOUNDARY_KEYS.contains(name)) {
        throw new WardException(
            "'"
                + key
                + "."
                + name
                + "' is not a key of a layer boundary. Supported keys: "
                + BOUNDARY_KEYS);
      }
    }
    try {
      return new LayerBoundary(names(key, table, FROM), names(key, table, FORBIDDEN));
    } catch (final IllegalArgumentException exception) {
      throw new WardException("'" + key + "': " + exception.getMessage(), exception);
    }
  }

  private static List<String> names(final String key, final TomlTable table, final String name) {
    final TomlArray array = table.getArray(name);
    if (array == null || array.isEmpty()) {
      throw new WardException(
          "'" + key + "." + name + "' must be a non-empty array of layer names");
    }
    final List<String> names = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      if (!array.isString(index)) {
        throw new WardException("'" + key + "." + name + "' must contain only layer names");
      }
      names.add(array.getString(index));
    }
    return List.copyOf(names);
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
