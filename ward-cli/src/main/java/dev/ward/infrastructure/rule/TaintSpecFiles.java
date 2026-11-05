package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.flow.taint.TaintEntry;
import dev.ward.analysis.domain.flow.taint.TaintSpec;
import dev.ward.analysis.domain.ir.FlowSiteKind;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Loads taint specification files from the classpath and validates each entry. A file is TOML with
 * the tables {@code sources}, {@code sinks}, {@code sanitizers} and {@code propagators}; each entry
 * has a {@code kind} (call, member or parameter), a {@code name}, and optionally a {@code receiver}
 * type and a {@code qualifier}. A sink entry also names its {@code arguments}, and a propagator may
 * name its {@code from} inputs.
 */
final class TaintSpecFiles {

  private static final Logger LOGGER = Logger.getLogger(TaintSpecFiles.class.getName());
  private static final String SOURCES = "sources";
  private static final String SINKS = "sinks";
  private static final String SANITIZERS = "sanitizers";
  private static final String PROPAGATORS = "propagators";
  private static final String KIND = "kind";
  private static final String NAME = "name";
  private static final String RECEIVER = "receiver";
  private static final String QUALIFIER = "qualifier";
  private static final String ARGUMENTS = "arguments";
  private static final String FROM = "from";
  private static final Set<String> TABLES = Set.of(SOURCES, SINKS, SANITIZERS, PROPAGATORS);
  private static final Set<String> KEYS = Set.of(KIND, NAME, RECEIVER, QUALIFIER, ARGUMENTS, FROM);
  private static final Map<String, FlowSiteKind> KINDS =
      Map.of(
          "call", FlowSiteKind.CALL,
          "member", FlowSiteKind.MEMBER_READ,
          "parameter", FlowSiteKind.PARAMETER);
  private static final Set<String> SOURCE_KINDS = Set.of("call", "member", "parameter");
  private static final Set<String> CALL_KIND = Set.of("call");

  private TaintSpecFiles() {}

  /**
   * Loads and joins the named specification files.
   *
   * @param paths classpath paths of the files, such as {@code taint/java/sql.toml}
   * @return the entries of all the files, in order
   * @throws RuleConfigurationException if a file is missing, is not TOML, or has a malformed entry
   */
  static TaintSpec load(final List<String> paths) {
    final List<TaintSpec> specs = new ArrayList<>();
    for (final String path : paths) {
      specs.add(parse(path, read(path)));
      LOGGER.fine(() -> "loaded taint specification " + path);
    }
    return TaintSpec.combine(specs);
  }

  private static String read(final String path) {
    try (InputStream in = TaintSpecFiles.class.getResourceAsStream("/" + path)) {
      if (in == null) {
        throw new RuleConfigurationException("taint specification " + path + " is not bundled");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException exception) {
      throw new RuleConfigurationException(
          "taint specification " + path + " cannot be read", exception);
    }
  }

  /** Parses one specification file; {@code file} names it in every error. */
  static TaintSpec parse(final String file, final String text) {
    final TomlParseResult toml = Toml.parse(text);
    if (toml.hasErrors()) {
      throw new RuleConfigurationException("taint specification " + file + " is not valid TOML");
    }
    for (final String table : toml.keySet()) {
      if (!TABLES.contains(table)) {
        throw new RuleConfigurationException(
            "taint specification " + file + " has an unknown table '" + table + "'");
      }
    }
    return new TaintSpec(
        entries(file, toml, SOURCES, SOURCE_KINDS),
        entries(file, toml, SINKS, CALL_KIND),
        entries(file, toml, SANITIZERS, CALL_KIND),
        entries(file, toml, PROPAGATORS, CALL_KIND));
  }

  private static List<TaintEntry> entries(
      final String file, final TomlParseResult toml, final String table, final Set<String> kinds) {
    final List<TaintEntry> found = new ArrayList<>();
    final TomlArray array = toml.getArray(table);
    if (array == null) {
      return found;
    }
    for (int index = 0; index < array.size(); index++) {
      if (!array.isTable(index)) {
        throw new RuleConfigurationException(
            "taint specification " + file + ": [" + table + "] must be a list of tables");
      }
      found.add(entry(file, table, index + 1, array.getTable(index), kinds));
    }
    return found;
  }

  private static TaintEntry entry(
      final String file,
      final String table,
      final int number,
      final TomlTable entry,
      final Set<String> kinds) {
    final String where = "entry " + number + " of [" + table + "] in " + file;
    for (final String key : entry.keySet()) {
      if (!KEYS.contains(key)) {
        throw new RuleConfigurationException(where + " has an unknown key '" + key + "'");
      }
    }
    final String kind = text(where, entry, KIND);
    if (!kinds.contains(kind)) {
      throw new RuleConfigurationException(
          where + " has kind '" + kind + "', which " + table + " do not accept");
    }
    final List<Integer> arguments = TaintSpecOptions.arguments(where, table, entry);
    if (PROPAGATORS.equals(table)) {
      TaintSpecOptions.from(where, entry);
    }
    return new TaintEntry(
        KINDS.get(kind),
        text(where, entry, NAME),
        optional(where, entry, RECEIVER),
        optional(where, entry, QUALIFIER),
        arguments);
  }

  private static String text(final String where, final TomlTable entry, final String key) {
    final String value = entry.getString(key);
    if (value == null || value.isBlank()) {
      throw new RuleConfigurationException(where + " has no " + key);
    }
    return value;
  }

  private static Optional<String> optional(
      final String where, final TomlTable entry, final String key) {
    if (!entry.contains(key)) {
      return Optional.empty();
    }
    final String value = entry.getString(key);
    if (value == null || value.isBlank()) {
      throw new RuleConfigurationException(where + " has an empty " + key);
    }
    return Optional.of(value);
  }
}
