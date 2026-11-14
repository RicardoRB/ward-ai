package dev.ward.infrastructure.config;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Parses the architecture tables of a {@code ward} check: {@code [checks.<name>.cycles]} with the
 * source {@code roots} whose slices must not form cycles, and {@code
 * [[checks.<name>.independence]]} with the {@code modules} that must not depend on each other.
 */
final class TomlArchitectureParser {
  static final String CYCLES = "cycles";
  static final String INDEPENDENCE = "independence";
  static final String ARCHITECTURE_FILE = "architecture-file";
  static final Set<String> CYCLE_KEYS = Set.of("roots");
  static final Set<String> INDEPENDENCE_KEYS = Set.of("modules");
  private static final String ROOTS = "roots";
  private static final String MODULES = "modules";
  private static final int MIN_MODULES = 2;
  private static final String PATH_SEPARATOR = "/";
  private static final String PROJECT_ROOT = ".";
  private static final String PARENT = "..";

  private TomlArchitectureParser() {}

  /**
   * Returns the project-relative path of the architecture rule file, or {@code null} when the check
   * uses {@code ward-architecture.toml}. The path must stay inside the project.
   */
  static String architectureFile(final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.ANALYSIS.equals(type) || !gate.contains(ARCHITECTURE_FILE)) {
      return null;
    }
    final String path = gate.isString(ARCHITECTURE_FILE) ? gate.getString(ARCHITECTURE_FILE) : null;
    if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..")) {
      throw new WardException(
          "'"
              + where
              + "."
              + ARCHITECTURE_FILE
              + "' must be a relative path inside the project, such as ward-architecture.toml");
    }
    return path;
  }

  /** Returns the cycle roots of a {@code ward} check, or an empty list when it sets none. */
  static List<String> cycleRoots(final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.ANALYSIS.equals(type) || !gate.contains(CYCLES)) {
      return List.of();
    }
    final TomlTable table = gate.getTable(CYCLES);
    if (table == null) {
      throw new WardException("'" + where + "." + CYCLES + "' must be a table with roots");
    }
    rejectUnknownKeys(where + "." + CYCLES, table, CYCLE_KEYS);
    return directories(where + "." + CYCLES + "." + ROOTS, table.getArray(ROOTS), 1);
  }

  /**
   * Returns the independence tables of a {@code ward} check, one module list per table, or an empty
   * list when it sets none.
   */
  static List<List<String>> independence(
      final String where, final String type, final TomlTable gate) {
    if (!SupportedQualityGates.ANALYSIS.equals(type) || !gate.contains(INDEPENDENCE)) {
      return List.of();
    }
    final TomlArray tables = gate.getArray(INDEPENDENCE);
    if (tables == null) {
      throw new WardException("'" + where + "." + INDEPENDENCE + "' must be an array of tables");
    }
    final List<List<String>> result = new ArrayList<>();
    for (int index = 0; index < tables.size(); index++) {
      result.add(table(where + "." + INDEPENDENCE, tables.getTable(index)));
    }
    return List.copyOf(result);
  }

  private static List<String> table(final String key, final TomlTable table) {
    if (table == null) {
      throw new WardException("'" + key + "' must be an array of tables");
    }
    rejectUnknownKeys(key, table, INDEPENDENCE_KEYS);
    return directories(key + "." + MODULES, table.getArray(MODULES), MIN_MODULES);
  }

  private static void rejectUnknownKeys(
      final String key, final TomlTable table, final Set<String> allowed) {
    for (final String name : table.keySet()) {
      if (!allowed.contains(name)) {
        throw new WardException(
            "'" + key + "." + name + "' is not a key of this table. Supported keys: " + allowed);
      }
    }
  }

  /**
   * Returns the project-relative directories of {@code array}, at least {@code minimum} of them.
   */
  private static List<String> directories(
      final String key, final TomlArray array, final int minimum) {
    if (array == null || array.size() < minimum) {
      throw new WardException(
          "'"
              + key
              + "' must be an array of at least "
              + minimum
              + " project-relative directories");
    }
    final List<String> directories = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      if (!array.isString(index)) {
        throw new WardException("'" + key + "' must contain only directory names");
      }
      directories.add(directory(key, array.getString(index)));
    }
    return List.copyOf(directories);
  }

  /**
   * Normalizes separators and trailing slashes, and rejects a path that is not inside the project.
   */
  private static String directory(final String key, final String text) {
    final String path = withoutTrailingSlashes(text.replace('\\', '/'));
    if (!isInsideProject(path)) {
      throw new WardException(
          "'" + key + "' names '" + text + "', which is not a project-relative directory");
    }
    return path;
  }

  private static String withoutTrailingSlashes(final String text) {
    String path = text;
    while (path.startsWith("./")) {
      path = path.substring(2);
    }
    while (path.endsWith(PATH_SEPARATOR) && path.length() > 1) {
      path = path.substring(0, path.length() - 1);
    }
    return path;
  }

  /** Whether {@code path} names a directory inside the project; {@code .} is the project itself. */
  private static boolean isInsideProject(final String path) {
    return PROJECT_ROOT.equals(path)
        || !path.isEmpty()
            && !path.startsWith(PATH_SEPARATOR)
            && !path.contains(",")
            && !path.contains(";")
            && Arrays.stream(path.split(PATH_SEPARATOR, -1)).noneMatch(PARENT::equals);
  }
}
