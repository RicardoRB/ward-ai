package dev.ward.analysis.application.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.application.rule.RuleFileReader;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleOptions;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.analysis.domain.rule.check.CheckedRule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

/**
 * Runs the fixtures of every rule whose check this module provides through the real rule engine, so
 * that this module's own coverage counts the checks it owns. The fixtures stay with the catalog
 * contract in ward-cli: the violating fixture must report exactly the expected {@code line:column}
 * positions and the passing one nothing, with the rule's default options or those of the case.
 */
class PythonFixturesTest {

  private static final Path FIXTURES =
      Path.of("..", "ward-cli", "src", "test", "resources", "rules");
  private static final String EXTENSION = "py";
  private static final String CHECK = "check";
  private static final String VIOLATING = "violating";

  /** A rule of this module that has fixtures, named by its id so that a failure names it. */
  record OwnedRule(RuleDefinition rule, Path directory) {
    @Override
    public String toString() {
      return rule.id();
    }
  }

  static Stream<OwnedRule> ownedRules() {
    final CheckProvider provider = new PythonCheckProvider();
    final Map<String, CheckFactory> factories = provider.checks();
    final List<OwnedRule> owned = new ArrayList<>();
    for (final String file : new PythonRuleFiles().ruleFiles()) {
      for (final RuleDefinition rule : RuleFileReader.read(file)) {
        final String name = Toml.parse(rule.body()).getString(CHECK);
        final Path directory = FIXTURES.resolve(rule.id().substring("ward:".length()));
        if (name != null && factories.containsKey(name) && Files.isDirectory(directory)) {
          owned.add(new OwnedRule(rule, directory));
        }
      }
    }
    return owned.stream();
  }

  @Test
  void theModuleOwnsRulesWithFixtures() {
    assertThat(ownedRules()).isNotEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("ownedRules")
  void violatingFixtureReportsExactlyTheExpectedPositionsAndPassingReportsNone(
      final OwnedRule owned) throws IOException {
    final List<String> cases = new ArrayList<>();
    cases.add("");
    try (Stream<Path> files = Files.list(owned.directory())) {
      files
          .map(file -> file.getFileName().toString())
          .filter(file -> file.startsWith(VIOLATING + "-"))
          .map(file -> file.substring(VIOLATING.length() + 1, file.lastIndexOf('.')))
          .sorted()
          .forEach(cases::add);
    }
    for (final String caseName : cases) {
      final String suffix = caseName.isEmpty() ? "" : "-" + caseName;
      final RuleDefinition rule = RuleOptions.apply(owned.rule(), options(owned, suffix));
      final List<String> expected =
          Files.readAllLines(owned.directory().resolve("expected" + suffix + ".txt")).stream()
              .filter(line -> !line.isBlank())
              .toList();
      assertThat(run(rule, owned.directory().resolve(VIOLATING + suffix + "." + EXTENSION)))
          .as("%s %s", owned, VIOLATING + suffix)
          .isEqualTo(expected);
      final Path passing = owned.directory().resolve("passing" + suffix + "." + EXTENSION);
      if (Files.isRegularFile(passing)) {
        assertThat(run(rule, passing)).as("%s passing%s", owned, suffix).isEmpty();
      }
    }
  }

  private static Map<String, Object> options(final OwnedRule owned, final String suffix)
      throws IOException {
    final Path file = owned.directory().resolve("options" + suffix + ".toml");
    final Map<String, Object> values = new LinkedHashMap<>();
    if (Files.isRegularFile(file)) {
      final TomlParseResult toml = Toml.parse(Files.readString(file, StandardCharsets.UTF_8));
      for (final String key : toml.keySet()) {
        final Object value = toml.get(key);
        values.put(key, value instanceof TomlArray array ? array.toList() : value);
      }
    }
    return values;
  }

  private static List<String> run(final RuleDefinition rule, final Path file) throws IOException {
    final Map<String, CheckFactory> factories = new PythonCheckProvider().checks();
    final RuleMatcher matcher = definition -> compile(definition, factories);
    final AnalysisLanguage language = rule.languages().iterator().next();
    final RuleRunResult result =
        new RuleEngine(matcher)
            .run(
                List.of(
                    new SourceUnit(
                        file.getFileName().toString(),
                        language,
                        Files.readString(file, StandardCharsets.UTF_8))),
                PythonFixtureFrontends.of(language),
                List.of(rule));
    assertThat(result.executionErrors()).as("execution errors of %s", rule.id()).isEmpty();
    assertThat(result.notRun()).as("rules not run: %s", rule.id()).isEmpty();
    return result.findings().stream()
        .filter(finding -> finding.ruleId().equals(rule.id()))
        .map(PythonFixturesTest::position)
        .toList();
  }

  private static CompiledRule compile(
      final RuleDefinition definition, final Map<String, CheckFactory> factories) {
    final TomlParseResult toml = Toml.parse(definition.body());
    return new CheckedRule(definition, factories.get(toml.getString(CHECK)).create(toml));
  }

  private static String position(final AnalysisFinding finding) {
    return finding.line() + ":" + finding.column();
  }
}
