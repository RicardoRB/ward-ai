package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.ArchitectureSelector;
import dev.ward.analysis.domain.rule.ExcludeGlob;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Reads a selector table of {@code ward-architecture.toml}: lists of globs, directory names and
 * external package or module names.
 */
final class ArchitectureSelectorReader {
  /** The key that names code outside the project. */
  static final String EXTERNAL = "external";

  /** The keys of a selector; the schema in the website lists the same ones. */
  static final Set<String> SELECTOR_KEYS = Set.of(
      "paths",
      "directories",
      "exclude-paths",
      EXTERNAL
  );

  private final ArchitectureProblems problems;

  ArchitectureSelectorReader(final ArchitectureProblems problems) {
    this.problems = problems;
  }

  /** Reads the selector of a constraint that names project files only. */
  ArchitectureSelector read(final TomlTable parent, final String label, final String key) {
    return read(parent, label, key, false);
  }

  /** Reads a selector; {@code external} is a problem unless {@code externalAllowed}. */
  ArchitectureSelector read(
      final TomlTable parent,
      final String label,
      final String key,
      final boolean externalAllowed
  ) {
    final TomlTable table = parent.isTable(key) ? parent.getTable(key) : null;
    if (table == null) {
      problems.add(label, key, parent.contains(key) ? "expected a table" : "required");
      return null;
    }
    final int before = problems.count();
    for (final String name : table.keySet()) {
      if (!SELECTOR_KEYS.contains(name)) {
        problems.add(label, key + "." + name, "unknown key; expected " + SELECTOR_KEYS);
      }
    }
    final List<String> paths = strings(table, label, key, "paths");
    final List<String> directories = strings(table, label, key, "directories");
    final List<String> excluded = strings(table, label, key, "exclude-paths");
    final List<String> external = names(table, label, key);
    if (!externalAllowed && table.contains(EXTERNAL)) {
      problems.add(label, key + "." + EXTERNAL, "external is read in must-not-depend-on only");
    }
    if (problems.count() > before) {
      return null;
    }
    try {
      return new ArchitectureSelector(paths, directories, excluded, external);
    } catch (final IllegalArgumentException exception) {
      problems.add(label, key, exception.getMessage());
      return null;
    }
  }

  private List<String> strings(
      final TomlTable table,
      final String label,
      final String parent,
      final String key
  ) {
    if (!table.contains(key)) {
      return List.of();
    }
    final TomlArray array = table.isArray(key) ? table.getArray(key) : null;
    final List<String> values = new ArrayList<>();
    for (int index = 0; array != null && index < array.size(); index++) {
      if (!array.isString(index) || array.getString(index).isEmpty()) {
        problems.add(label, parent + "." + key, "expected a list of non-empty strings");
        return List.of();
      }
      values.add(array.getString(index));
    }
    if (array == null) {
      problems.add(label, parent + "." + key, "expected a list of non-empty strings");
    }
    if (!EXTERNAL.equals(key)) {
      values.forEach(value -> compiles(label, parent + "." + key, value));
    }
    return values;
  }

  /** Reads {@code external}: names, not globs, so a wildcard is a problem. */
  private List<String> names(final TomlTable table, final String label, final String parent) {
    final List<String> values = strings(table, label, parent, EXTERNAL);
    if (values.stream().anyMatch(value -> value.contains("*"))) {
      problems.add(
          label,
          parent + "." + EXTERNAL,
          "expected package or module names without '*'; a name also matches everything below it"
      );
    }
    return values;
  }

  boolean fileName(final String label, final String key, final String glob) {
    if (glob.contains("/")) {
      problems.add(label, key, "expected a file name pattern without '/', got \"" + glob + "\"");
      return false;
    }
    return compiles(label, key, glob);
  }

  private boolean compiles(final String label, final String key, final String glob) {
    try {
      ExcludeGlob.compile(glob);
      return true;
    } catch (final IllegalArgumentException exception) {
      problems.add(label, key, "not a valid glob \"" + glob + "\": " + exception.getMessage());
      return false;
    }
  }

  String text(final TomlTable table, final String label, final String key) {
    final String value = table.isString(key) ? table.getString(key) : null;
    if (value == null || value.isBlank()) {
      problems.add(label, key, table.contains(key) ? "expected a non-empty string" : "required");
      return null;
    }
    return value;
  }
}
