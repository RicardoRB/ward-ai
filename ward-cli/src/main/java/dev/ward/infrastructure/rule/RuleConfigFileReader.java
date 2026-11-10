package dev.ward.infrastructure.rule;

import dev.ward.application.gate.RuleConfigFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads {@code ward-rules.toml}. It checks the shape of the file (the version, the {@code [select]}
 * keys and their types, and the rule tables) and reports each problem with the file, the table and
 * the key. A rule table may be written {@code [rules."ward:<group>/<name>"]} or {@code
 * [rules.<group>.<name>]}, and both name the same rule. Whether the rules and options exist is
 * checked later.
 */
final class RuleConfigFileReader {
  private static final Logger LOGGER = Logger.getLogger(RuleConfigFileReader.class.getName());
  private static final String VERSION = "version";
  private static final String SELECT = "select";
  private static final String RULES = "rules";
  private static final long SUPPORTED_VERSION = 1;
  private static final Set<String> TOP_LEVEL = Set.of(VERSION, SELECT, RULES);
  private static final Set<String> SELECT_KEYS =
      Set.of("preset", "languages", "categories", "include", "exclude");

  private final String name;
  private final List<String> problems = new ArrayList<>();

  private RuleConfigFileReader(final String name) {
    this.name = name;
  }

  /**
   * Reads the file {@code relative} under {@code root}, or {@code ward-rules.toml} when it is
   * {@code null}. Returns empty when the default file does not exist.
   */
  static Optional<RuleConfigFile> read(final Path root, final String relative) {
    final String name = relative == null ? RuleConfigFile.DEFAULT_NAME : relative;
    final Path file = root.resolve(name).normalize();
    if (!file.startsWith(root.normalize())) {
      return Optional.of(RuleConfigFile.unusable(name, "the file must stay inside the project"));
    }
    if (!Files.isRegularFile(file)) {
      return relative == null
          ? Optional.empty()
          : Optional.of(RuleConfigFile.unusable(name, "the file does not exist"));
    }
    try {
      LOGGER.log(Level.FINE, "event=rule-config-read file={0}", name);
      return Optional.of(new RuleConfigFileReader(name).parse(Toml.parse(file)));
    } catch (final IOException exception) {
      return Optional.of(RuleConfigFile.unusable(name, "the file could not be read"));
    }
  }

  private RuleConfigFile parse(final TomlParseResult toml) {
    if (toml.hasErrors()) {
      for (final TomlParseError error : toml.errors()) {
        problems.add(name + ": not valid TOML at " + error.position() + ": " + error.getMessage());
      }
      return new RuleConfigFile(name, RuleConfigFile.Select.EMPTY, Map.of(), problems);
    }
    for (final String key : toml.keySet()) {
      if (!TOP_LEVEL.contains(key)) {
        problem("", key, "unknown key; expected " + List.of(VERSION, SELECT, RULES));
      }
    }
    version(toml);
    final RuleTableReader tables = new RuleTableReader(name, problems);
    final Map<String, Map<String, Object>> rules = tables.read(toml);
    return new RuleConfigFile(name, select(toml), rules, tables.headers(), problems);
  }

  private void version(final TomlParseResult toml) {
    final Object version = toml.get(VERSION);
    if (!Long.valueOf(SUPPORTED_VERSION).equals(version)) {
      problem("", VERSION, "expected " + SUPPORTED_VERSION + ", got " + TomlTree.show(version));
    }
  }

  private RuleConfigFile.Select select(final TomlParseResult toml) {
    if (!toml.contains(SELECT)) {
      return RuleConfigFile.Select.EMPTY;
    }
    final TomlTable table = toml.isTable(SELECT) ? toml.getTable(SELECT) : null;
    if (table == null) {
      problem("", SELECT, "expected a table");
      return RuleConfigFile.Select.EMPTY;
    }
    for (final String key : table.keySet()) {
      if (!SELECT_KEYS.contains(key)) {
        problem("[select]", key, "unknown key; expected " + SELECT_KEYS);
      }
    }
    return new RuleConfigFile.Select(
        text(table, "preset"),
        strings(table, "languages"),
        strings(table, "categories"),
        strings(table, "include"),
        strings(table, "exclude"));
  }

  private String text(final TomlTable table, final String key) {
    if (!table.contains(key)) {
      return null;
    }
    if (!table.isString(key)) {
      problem("[select]", key, "expected a string, got " + TomlTree.show(table.get(key)));
      return null;
    }
    return table.getString(key);
  }

  private List<String> strings(final TomlTable table, final String key) {
    if (!table.contains(key)) {
      return List.of();
    }
    final List<Object> values = TomlTree.list(table.get(key));
    if (values.stream().allMatch(String.class::isInstance) && table.isArray(key)) {
      return values.stream().map(String.class::cast).toList();
    }
    problem("[select]", key, "expected a list of strings, got " + TomlTree.show(table.get(key)));
    return List.of();
  }

  private void problem(final String table, final String key, final String detail) {
    final String where = (table.isEmpty() ? "" : " " + table) + (key.isEmpty() ? "" : " " + key);
    problems.add(name + ":" + where + ": " + detail);
  }
}
