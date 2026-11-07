package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads an analysis rule file from the classpath. A file is TOML with one {@code [[rules]]} table
 * per rule, each holding {@code id}, {@code language}, {@code capability}, {@code severity} and
 * {@code body}. A file that cannot be read fails as a whole with an error that names it.
 */
public final class RuleFileReader {

  private static final String RULES = "rules";
  private static final String ID = "id";
  private static final String LANGUAGE = "language";
  private static final String CAPABILITY = "capability";
  private static final String SEVERITY = "severity";
  private static final String BODY = "body";

  private RuleFileReader() {}

  /**
   * Reads one rule file.
   *
   * @throws dev.ward.analysis.domain.rule.RuleConfigurationException if the file is missing, is not
   *     valid TOML, or holds an unusable rule
   */
  public static List<RuleDefinition> read(final String resource) {
    final TomlParseResult toml = parseResource(resource);
    final TomlArray tables = toml.getArray(RULES);
    final List<RuleDefinition> rules = new ArrayList<>();
    for (int index = 0; tables != null && index < tables.size(); index++) {
      rules.add(rule(resource, tables.getTable(index)));
    }
    return rules;
  }

  private static TomlParseResult parseResource(final String resource) {
    try (InputStream stream = open(resource)) {
      return parse(resource, stream);
    } catch (final IOException exception) {
      throw RuleFileProblems.problem(resource, "the file could not be read", exception);
    }
  }

  private static InputStream open(final String resource) {
    final InputStream stream = RuleFileReader.class.getResourceAsStream("/" + resource);
    if (stream == null) {
      throw RuleFileProblems.problem(resource, "the file is missing from the Ward distribution");
    }
    return stream;
  }

  private static TomlParseResult parse(final String resource, final InputStream stream)
      throws IOException {
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      final TomlParseResult toml = Toml.parse(reader);
      if (toml.hasErrors()) {
        throw RuleFileProblems.problem(resource, "it is not valid TOML");
      }
      return toml;
    }
  }

  private static RuleDefinition rule(final String resource, final TomlTable table) {
    if (table == null) {
      throw RuleFileProblems.problem(resource, "every entry under [[rules]] must be a table");
    }
    final AnalysisLanguage language = constant(resource, table, LANGUAGE, AnalysisLanguage.class);
    final RuleCapability capability = constant(resource, table, CAPABILITY, RuleCapability.class);
    final AnalysisSeverity severity = constant(resource, table, SEVERITY, AnalysisSeverity.class);
    final RuleDefinition definition;
    try {
      definition =
          new RuleDefinition(
              text(resource, table, ID),
              Set.of(language),
              capability,
              severity,
              text(resource, table, BODY),
              RuleRowMetadata.category(table),
              RuleRowMetadata.presets(table),
              RuleRowMetadata.options(table));
    } catch (final IllegalArgumentException exception) {
      throw RuleFileProblems.problem(resource, exception.getMessage(), exception);
    }
    return definition;
  }

  private static String text(final String resource, final TomlTable table, final String key) {
    final String value = table.getString(key);
    if (value == null) {
      throw RuleFileProblems.problem(resource, "'" + key + "' must be a string");
    }
    return value;
  }

  private static <E extends Enum<E>> E constant(
      final String resource, final TomlTable table, final String key, final Class<E> type) {
    final String value = text(resource, table, key);
    try {
      return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException exception) {
      throw RuleFileProblems.problem(
          resource, "'" + key + "' value '" + value + "' is not supported", exception);
    }
  }
}
