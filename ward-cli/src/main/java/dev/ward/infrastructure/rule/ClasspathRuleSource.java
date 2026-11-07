package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.RuleDefinitions;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.port.AnalysisFailedException;
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
import java.util.Set;
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

  /** The rule files packaged with Ward, in load order. */
  static final List<String> RULE_FILES =
      List.of(
          "rules/java/size.toml",
          "rules/java/imports.toml",
          "rules/java/naming.toml",
          "rules/java/dataflow.toml",
          "rules/java/security.toml",
          "rules/java/bestpractices.toml",
          "rules/java/design.toml",
          "rules/java/correctness.toml",
          "rules/java/architecture.toml",
          "rules/java/errorprone.toml",
          "dev/ward/analysis/lang/node/rules.toml",
          "rules/python-structural.toml",
          "rules/python/pycodestyle.toml",
          "rules/python/bugbear.toml",
          "rules/python/flake8-comprehensions.toml",
          "rules/python/flake8-errmsg.toml",
          "rules/python/flake8-implicit-str-concat.toml",
          "rules/python/pygrep-hooks.toml",
          "rules/python/refurb.toml",
          "rules/python/pyflakes.toml",
          "rules/python/pylint.toml",
          "rules/python/flake8-2020.toml",
          "rules/python/flake8-debugger.toml",
          "rules/swift-structural.toml",
          "rules/swift/part-3.toml",
          "rules/swift/performance.toml",
          "rules/swift/idiomatic.toml",
          "rules/swift/lint.toml",
          "rules/swift/idiomatic-2.toml",
          "rules/kotlin/size.toml",
          "rules/kotlin/naming.toml",
          "rules/kotlin/exceptions.toml",
          "rules/kotlin/coroutines.toml",
          "rules/kotlin/performance.toml",
          "rules/kotlin/potential-bugs.toml");

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
  public ClasspathRuleSource(final List<String> resources) {
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
    final InputStream stream = ClasspathRuleSource.class.getResourceAsStream("/" + resource);
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
    checkBody(resource, definition);
    return definition;
  }

  /** Builds the rule once so that a body that cannot be used is reported with its file. */
  private static void checkBody(final String resource, final RuleDefinition definition) {
    try {
      BundledRuleMatchers.create().compile(definition);
    } catch (final AnalysisFailedException exception) {
      throw RuleFileProblems.problem(
          resource,
          "rule '" + definition.id() + "' cannot be used: " + exception.getMessage(),
          exception);
    }
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
