package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.RuleDefinitions;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.application.gate.AnalysisRuleLoad;
import dev.ward.application.gate.AnalysisRuleSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Loads analysis rule files packaged as classpath resources. A file is TOML with one {@code
 * [[rules]]} table per rule, each holding {@code id}, {@code language}, {@code capability}, {@code
 * severity} and {@code body}. A file that cannot be used is skipped as a whole and reported.
 */
public final class ClasspathRuleSource implements AnalysisRuleSource {

  /** The rule files packaged with Ward, one per language that has bundled rules. */
  static final List<String> RULE_FILES =
      List.of("dev/ward/analysis/lang/node/rules.toml", "rules/python-structural.toml");

  private static final String RULES = "rules";
  private static final String ID = "id";
  private static final String LANGUAGE = "language";
  private static final String CAPABILITY = "capability";
  private static final String SEVERITY = "severity";
  private static final String BODY = "body";

  private final List<String> resources;

  /** Creates the source that loads the packaged rule files. */
  @Inject
  public ClasspathRuleSource() {
    this(RULE_FILES);
  }

  /** Creates the source that loads the given classpath resources, in order. */
  ClasspathRuleSource(final List<String> resources) {
    this.resources = List.copyOf(resources);
  }

  @Override
  public AnalysisRuleLoad load() {
    final List<RuleDefinition> rules = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    for (final String resource : resources) {
      try {
        final List<RuleDefinition> file = read(resource);
        RuleDefinitions.requireUnique(concat(rules, file));
        rules.addAll(file);
      } catch (final RuleConfigurationException exception) {
        errors.add(exception.getMessage());
      }
    }
    return new AnalysisRuleLoad(rules, errors);
  }

  private static List<RuleDefinition> concat(
      final List<RuleDefinition> loaded, final List<RuleDefinition> file) {
    final List<RuleDefinition> all = new ArrayList<>(loaded);
    all.addAll(file);
    return all;
  }

  /** Reads one rule file; throws a configuration error that names the file. */
  private static List<RuleDefinition> read(final String resource) {
    final TomlParseResult toml;
    try (InputStream stream = ClasspathRuleSource.class.getResourceAsStream("/" + resource)) {
      if (stream == null) {
        throw problem(resource, "the file is missing from the Ward distribution");
      }
      toml = parse(resource, stream);
    } catch (final IOException exception) {
      throw problem(resource, "the file could not be read", exception);
    }
    final TomlArray tables = toml.getArray(RULES);
    final List<RuleDefinition> rules = new ArrayList<>();
    for (int index = 0; tables != null && index < tables.size(); index++) {
      rules.add(rule(resource, tables.getTable(index)));
    }
    return rules;
  }

  private static TomlParseResult parse(final String resource, final InputStream stream)
      throws IOException {
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      final TomlParseResult toml = Toml.parse(reader);
      if (toml.hasErrors()) {
        throw problem(resource, "it is not valid TOML");
      }
      return toml;
    }
  }

  private static RuleDefinition rule(final String resource, final TomlTable table) {
    if (table == null) {
      throw problem(resource, "every entry under [[rules]] must be a table");
    }
    final AnalysisLanguage language = constant(resource, table, LANGUAGE, AnalysisLanguage.class);
    final RuleCapability capability = constant(resource, table, CAPABILITY, RuleCapability.class);
    final AnalysisSeverity severity = constant(resource, table, SEVERITY, AnalysisSeverity.class);
    try {
      return new RuleDefinition(
          text(resource, table, ID), language, capability, severity, text(resource, table, BODY));
    } catch (final IllegalArgumentException exception) {
      throw problem(resource, exception.getMessage(), exception);
    }
  }

  private static String text(final String resource, final TomlTable table, final String key) {
    final String value = table.getString(key);
    if (value == null) {
      throw problem(resource, "'" + key + "' must be a string");
    }
    return value;
  }

  private static <E extends Enum<E>> E constant(
      final String resource, final TomlTable table, final String key, final Class<E> type) {
    final String value = text(resource, table, key);
    try {
      return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException exception) {
      throw problem(resource, "'" + key + "' value '" + value + "' is not supported", exception);
    }
  }

  private static RuleConfigurationException problem(final String resource, final String detail) {
    return new RuleConfigurationException(message(resource, detail));
  }

  private static RuleConfigurationException problem(
      final String resource, final String detail, final Throwable cause) {
    return new RuleConfigurationException(message(resource, detail), cause);
  }

  private static String message(final String resource, final String detail) {
    return "Analysis rule file '"
        + resource
        + "' cannot be used: "
        + detail
        + ". Its rules are skipped.";
  }
}
