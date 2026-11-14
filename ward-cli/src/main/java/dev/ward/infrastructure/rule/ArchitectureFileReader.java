package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.rule.ArchitectureConstraint;
import dev.ward.analysis.domain.rule.ArchitectureRule;
import dev.ward.analysis.domain.rule.ArchitectureSelector;
import dev.ward.analysis.domain.rule.ExcludeGlob;
import dev.ward.application.gate.ArchitectureFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads {@code ward-architecture.toml}: a {@code version} and {@code [[rule]]} tables. It checks
 * the whole shape and reports each problem with the file, the rule and the key. It starts no
 * process and reads no other file.
 */
final class ArchitectureFileReader {
  /** The keys of the file; the schema in the website lists the same ones. */
  static final Set<String> TOP_LEVEL = Set.of("version", "rule");

  /** The keys of a selector. */
  static final Set<String> SELECTOR_KEYS = Set.of("paths", "directories", "exclude-paths");

  /** The keys of a {@code [[rule]]} table that are not constraints. */
  static final Set<String> RULE_KEYS = Set.of("name", "select", "because", "allow-empty");

  private static final Logger LOGGER = Logger.getLogger(ArchitectureFileReader.class.getName());
  private static final String VERSION = "version";
  private static final String RULE = "rule";
  private static final String NAME = "name";
  private static final String BECAUSE = "because";
  private static final String SELECT = "select";
  private static final String ALLOW_EMPTY = "allow-empty";
  private static final long SUPPORTED_VERSION = 1;

  private final String file;
  private final List<String> problems = new ArrayList<>();

  private ArchitectureFileReader(final String file) {
    this.file = file;
  }

  /**
   * Reads the file {@code relative} under {@code root}, or {@code ward-architecture.toml} when it
   * is {@code null}. Returns empty when the default file does not exist.
   */
  static Optional<ArchitectureFile> read(final Path root, final String relative) {
    final String name = relative == null ? ArchitectureFile.DEFAULT_NAME : relative;
    final Path path = root.resolve(name).normalize();
    if (!path.startsWith(root.normalize())) {
      return Optional.of(ArchitectureFile.unusable(name, "the file must stay inside the project"));
    }
    if (!Files.isRegularFile(path)) {
      return relative == null
          ? Optional.empty()
          : Optional.of(ArchitectureFile.unusable(name, "the file does not exist"));
    }
    try {
      LOGGER.log(Level.FINE, "event=architecture-file-read file={0}", name);
      return Optional.of(new ArchitectureFileReader(name).parse(Toml.parse(path)));
    } catch (final IOException exception) {
      return Optional.of(ArchitectureFile.unusable(name, "the file could not be read"));
    }
  }

  private ArchitectureFile parse(final TomlParseResult toml) {
    if (toml.hasErrors()) {
      for (final TomlParseError error : toml.errors()) {
        problems.add(file + ": not valid TOML at " + error.position() + ": " + error.getMessage());
      }
      return new ArchitectureFile(file, List.of(), problems);
    }
    for (final String key : toml.keySet()) {
      if (!TOP_LEVEL.contains(key)) {
        problem("", key, "unknown key; expected " + List.of(VERSION, RULE));
      }
    }
    if (!Long.valueOf(SUPPORTED_VERSION).equals(toml.get(VERSION))) {
      problem("", VERSION, "expected " + SUPPORTED_VERSION + ", got " + toml.get(VERSION));
    }
    final List<ArchitectureRule> rules = rules(toml);
    return new ArchitectureFile(file, problems.isEmpty() ? rules : List.of(), problems);
  }

  private List<ArchitectureRule> rules(final TomlParseResult toml) {
    if (!toml.contains(RULE)) {
      return List.of();
    }
    if (!toml.isArray(RULE) || !allTables(toml.getArray(RULE))) {
      problem("", RULE, "expected [[rule]] tables");
      return List.of();
    }
    final TomlArray tables = toml.getArray(RULE);
    final Set<String> names = new HashSet<>();
    final List<ArchitectureRule> rules = new ArrayList<>();
    for (int index = 0; index < tables.size(); index++) {
      rule(tables.getTable(index), index, names).ifPresent(rules::add);
    }
    return rules;
  }

  private static boolean allTables(final TomlArray array) {
    return IntStream.range(0, array.size()).allMatch(array::isTable);
  }

  private Optional<ArchitectureRule> rule(
      final TomlTable table, final int index, final Set<String> names) {
    final int before = problems.size();
    final String label = table.isString(NAME) ? table.getString(NAME) : "#" + (index + 1);
    for (final String key : table.keySet()) {
      if (!RULE_KEYS.contains(key) && constraintKind(key) == null) {
        problem(label, key, "unknown key; expected " + supportedKeys());
      }
    }
    final String name = text(table, label, NAME);
    if (name != null && !names.add(name)) {
      problem(label, NAME, "the name is used by another rule");
    }
    final String because = text(table, label, BECAUSE);
    final boolean allowEmpty = allowEmpty(table, label);
    final ArchitectureSelector select = selector(table, label, SELECT);
    final ArchitectureConstraint constraint = constraint(table, label);
    if (problems.size() > before) {
      return Optional.empty();
    }
    return Optional.of(new ArchitectureRule(name, select, constraint, because, allowEmpty));
  }

  private static List<String> supportedKeys() {
    final List<String> keys = new ArrayList<>(List.of(NAME, SELECT));
    for (final ArchitectureConstraint.Kind kind : ArchitectureConstraint.Kind.values()) {
      keys.add(kind.key());
    }
    keys.addAll(List.of(BECAUSE, ALLOW_EMPTY));
    return keys;
  }

  private static ArchitectureConstraint.Kind constraintKind(final String key) {
    for (final ArchitectureConstraint.Kind kind : ArchitectureConstraint.Kind.values()) {
      if (kind.key().equals(key)) {
        return kind;
      }
    }
    return null;
  }

  private ArchitectureConstraint constraint(final TomlTable table, final String label) {
    final List<ArchitectureConstraint.Kind> kinds =
        table.keySet().stream()
            .map(ArchitectureFileReader::constraintKind)
            .filter(Objects::nonNull)
            .toList();
    if (kinds.size() != 1) {
      problem(
          label,
          kinds.isEmpty()
              ? "constraint"
              : kinds.stream().map(ArchitectureConstraint.Kind::key).toList().toString(),
          kinds.isEmpty()
              ? "expected one of " + keys()
              : "a rule has exactly one constraint, got " + kinds.size());
      return null;
    }
    final ArchitectureConstraint.Kind kind = kinds.getFirst();
    if (kind == ArchitectureConstraint.Kind.FILE_NAME) {
      final String glob = text(table, label, kind.key());
      return glob == null || !validGlob(label, kind.key(), glob)
          ? null
          : new ArchitectureConstraint(kind, null, glob);
    }
    final ArchitectureSelector target = selector(table, label, kind.key());
    return target == null ? null : new ArchitectureConstraint(kind, target, null);
  }

  private static List<String> keys() {
    return Arrays.stream(ArchitectureConstraint.Kind.values())
        .map(ArchitectureConstraint.Kind::key)
        .toList();
  }

  private boolean validGlob(final String label, final String key, final String glob) {
    if (glob.contains("/")) {
      problem(label, key, "expected a file name pattern without '/', got \"" + glob + "\"");
      return false;
    }
    return compiles(label, key, glob);
  }

  private boolean compiles(final String label, final String key, final String glob) {
    try {
      ExcludeGlob.compile(glob);
      return true;
    } catch (final IllegalArgumentException exception) {
      problem(label, key, "not a valid glob \"" + glob + "\": " + exception.getMessage());
      return false;
    }
  }

  private ArchitectureSelector selector(
      final TomlTable parent, final String label, final String key) {
    if (!parent.isTable(key)) {
      problem(label, key, parent.contains(key) ? "expected a table" : "required");
      return null;
    }
    final TomlTable table = parent.getTable(key);
    final int before = problems.size();
    for (final String name : table.keySet()) {
      if (!SELECTOR_KEYS.contains(name)) {
        problem(label, key + "." + name, "unknown key; expected " + SELECTOR_KEYS);
      }
    }
    final List<String> paths = strings(table, label, key, "paths");
    final List<String> directories = strings(table, label, key, "directories");
    final List<String> excluded = strings(table, label, key, "exclude-paths");
    if (problems.size() > before) {
      return null;
    }
    try {
      return new ArchitectureSelector(paths, directories, excluded);
    } catch (final IllegalArgumentException exception) {
      problem(label, key, exception.getMessage());
      return null;
    }
  }

  private List<String> strings(
      final TomlTable table, final String label, final String parent, final String key) {
    if (!table.contains(key)) {
      return List.of();
    }
    final TomlArray array = table.isArray(key) ? table.getArray(key) : null;
    final List<String> values = new ArrayList<>();
    for (int index = 0; array != null && index < array.size(); index++) {
      if (!array.isString(index) || array.getString(index).isEmpty()) {
        problem(label, parent + "." + key, "expected a list of non-empty strings");
        return List.of();
      }
      values.add(array.getString(index));
    }
    if (array == null) {
      problem(label, parent + "." + key, "expected a list of non-empty strings");
    }
    values.forEach(value -> compiles(label, parent + "." + key, value));
    return values;
  }

  private String text(final TomlTable table, final String label, final String key) {
    if (!table.isString(key) || table.getString(key).isBlank()) {
      problem(label, key, table.contains(key) ? "expected a non-empty string" : "required");
      return null;
    }
    return table.getString(key);
  }

  private boolean allowEmpty(final TomlTable table, final String label) {
    if (!table.contains(ALLOW_EMPTY)) {
      return false;
    }
    if (!table.isBoolean(ALLOW_EMPTY)) {
      problem(label, ALLOW_EMPTY, "expected true or false");
      return false;
    }
    return table.getBoolean(ALLOW_EMPTY);
  }

  private void problem(final String rule, final String key, final String detail) {
    final String where =
        (rule.isEmpty() ? "" : " rule '" + rule + "'") + (key.isEmpty() ? "" : " " + key);
    problems.add(file + ":" + where + ": " + detail);
  }
}
