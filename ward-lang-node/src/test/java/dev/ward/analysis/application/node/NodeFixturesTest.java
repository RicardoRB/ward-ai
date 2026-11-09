package dev.ward.analysis.application.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.CheckFactory;
import dev.ward.analysis.application.rule.FrontendSupport;
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
import dev.ward.analysis.domain.rule.check.ProjectCheck;
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
 * that this module's own coverage counts the checks it owns. The fixtures stay in ward-cli: the
 * violating fixture must report exactly the expected {@code line:column} positions and the passing
 * one nothing, with the rule's default options or those of the case.
 */
class NodeFixturesTest {

  private static final Path FIXTURES =
      Path.of("..", "ward-cli", "src", "test", "resources", "rules");
  private static final String CHECK = "check";
  private static final String VIOLATING = "violating";
  private static final String PASSING = "passing";

  /** A rule of this module that has fixtures, named by its id so that a failure names it. */
  record OwnedRule(RuleDefinition rule, Path directory) {
    @Override
    public String toString() {
      return rule.id();
    }
  }

  static Stream<OwnedRule> ownedRules() {
    final Map<String, CheckFactory> factories = new NodeCheckProvider().checks();
    final List<OwnedRule> owned = new ArrayList<>();
    for (final String file : new NodeRuleFiles().ruleFiles()) {
      for (final RuleDefinition rule : RuleFileReader.read(file)) {
        final String name = Toml.parse(rule.body()).getString(CHECK);
        final Path directory = FIXTURES.resolve(rule.id().substring("ward:".length()));
        if (name != null
            && factories.containsKey(name)
            && Files.isDirectory(directory)
            && !projectCheck(rule, factories)) {
          owned.add(new OwnedRule(rule, directory));
        }
      }
    }
    return owned.stream();
  }

  /**
   * Whether the rule's check reads the whole project (manifests, other files). Those run in
   * ward-cli's RuleCatalogFixturesTest with a fixture project and have direct unit tests here.
   */
  private static boolean projectCheck(
      final RuleDefinition rule, final Map<String, CheckFactory> factories) {
    final TomlParseResult toml = Toml.parse(rule.body());
    return factories.get(toml.getString(CHECK)).create(toml) instanceof ProjectCheck;
  }

  @Test
  void theModuleOwnsRulesWithFixtures() {
    assertThat(ownedRules()).isNotEmpty();
  }

  /** Every rule whose check the provider has must have a fixture folder: no rule is skipped. */
  @Test
  void everyRuleOfTheProviderHasFixtures() {
    final Map<String, CheckFactory> factories = new NodeCheckProvider().checks();
    final List<String> missing = new ArrayList<>();
    for (final String file : new NodeRuleFiles().ruleFiles()) {
      for (final RuleDefinition rule : RuleFileReader.read(file)) {
        final String name = Toml.parse(rule.body()).getString(CHECK);
        final Path directory = FIXTURES.resolve(rule.id().substring("ward:".length()));
        if (name != null && factories.containsKey(name) && !Files.isDirectory(directory)) {
          missing.add(rule.id());
        }
      }
    }
    assertThat(missing).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("ownedRules")
  void violatingFixtureReportsExactlyTheExpectedPositionsAndPassingReportsNone(
      final OwnedRule owned) throws IOException {
    final String extension = extension(owned.rule());
    for (final String caseName : cases(owned.directory())) {
      final String suffix = caseName.isEmpty() ? "" : "-" + caseName;
      final RuleDefinition rule = RuleOptions.apply(owned.rule(), options(owned, suffix));
      final List<String> expected =
          Files.readAllLines(owned.directory().resolve("expected" + suffix + ".txt")).stream()
              .filter(line -> !line.isBlank())
              .toList();
      assertThat(run(rule, owned.directory().resolve(VIOLATING + suffix + "." + extension), owned))
          .as("%s %s", owned, VIOLATING + suffix)
          .isEqualTo(expected);
      final Path passing = owned.directory().resolve(PASSING + suffix + "." + extension);
      if (Files.isRegularFile(passing)) {
        assertThat(run(rule, passing, owned)).as("%s passing%s", owned, suffix).isEmpty();
      }
    }
  }

  /** The default case, then one per {@code violating-<case>} file. */
  private static List<String> cases(final Path directory) throws IOException {
    final List<String> cases = new ArrayList<>();
    cases.add("");
    try (Stream<Path> files = Files.list(directory)) {
      files
          .map(file -> file.getFileName().toString())
          .filter(file -> file.startsWith(VIOLATING + "-"))
          .map(file -> file.substring(VIOLATING.length() + 1, file.lastIndexOf('.')))
          .sorted()
          .forEach(cases::add);
    }
    return cases;
  }

  /** The extension of the rule's language: TypeScript fixtures are {@code .ts}. */
  private static String extension(final RuleDefinition rule) {
    return rule.languages().iterator().next() == AnalysisLanguage.TYPESCRIPT ? "ts" : "js";
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

  private static List<String> run(final RuleDefinition rule, final Path file, final OwnedRule owned)
      throws IOException {
    final Map<String, CheckFactory> factories = new NodeCheckProvider().checks();
    final RuleMatcher matcher = definition -> compile(definition, factories);
    final RuleEngine engine = new RuleEngine(matcher);
    final AnalysisLanguage language = rule.languages().iterator().next();
    final List<SourceUnit> units =
        List.of(
            new SourceUnit(
                file.getFileName().toString(),
                language,
                Files.readString(file, StandardCharsets.UTF_8)));
    final List<FrontendSupport> frontends = NodeFixtureFrontends.forFolder(owned.directory());
    final RuleRunResult result = engine.run(units, frontends, List.of(rule));
    assertThat(result.executionErrors()).as("execution errors of %s", rule.id()).isEmpty();
    assertThat(result.notRun()).as("rules not run: %s", rule.id()).isEmpty();
    final List<AnalysisFinding> all = new ArrayList<>(result.findings());
    all.addAll(engine.runProject(units, frontends, List.of(rule)));
    return all.stream()
        .filter(finding -> finding.ruleId().equals(rule.id()))
        .map(finding -> finding.line() + ":" + finding.column())
        .toList();
  }

  private static CompiledRule compile(
      final RuleDefinition definition, final Map<String, CheckFactory> factories) {
    final TomlParseResult toml = Toml.parse(definition.body());
    return new CheckedRule(definition, factories.get(toml.getString(CHECK)).create(toml));
  }
}
