package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.application.rule.RuleEngine;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.TextFile;
import dev.ward.analysis.domain.port.TypeFacts;
import dev.ward.analysis.domain.rule.GraphRules;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOptions;
import dev.ward.analysis.domain.rule.RuleRunResult;
import dev.ward.infrastructure.rule.BundledRuleMatchers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every implemented (row, language) pair of the catalog through the real rule engine with the
 * rule's default options: the violating fixture must report exactly the expected positions, and the
 * passing fixture must report nothing. A later step adds fixtures and no test code.
 */
class RuleCatalogFixturesTest {

  private static final RuleCatalog CATALOG = new RuleCatalogReader().load();
  private static final Map<String, RuleDefinition> RULES =
      CatalogRuleDefinitions.all().stream()
          .collect(Collectors.toMap(RuleDefinition::id, rule -> rule));

  /** One implemented pair, named by its rule id so that a failure names it. */
  record Pair(CatalogRow row, AnalysisLanguage language) {
    @Override
    public String toString() {
      return row.idFor(language);
    }
  }

  static Stream<Pair> pairs() {
    return implemented().stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("pairs")
  void violatingFixtureReportsExactlyTheExpectedPositionsAndPassingReportsNone(final Pair pair)
      throws IOException {
    final String id = pair.toString();
    final RuleDefinition rule = RULES.get(id);
    assertThat(rule).as("rule definition %s", id).isNotNull();
    final RuleFixtureSet fixtures =
        RuleFixtureSet.of(RuleFixtureSet.root(), pair.row(), pair.language());
    assertThat(fixtures.complete()).as("fixtures of %s in %s", id, fixtures.directory()).isTrue();
    final TypeFacts typeFacts = FixtureTypeFacts.of(fixtures.typeFacts());
    final RuleFixtureRunner runner = new RuleFixtureRunner();
    final List<String> problems = new ArrayList<>();
    for (final String caseName : fixtures.cases()) {
      final RuleDefinition configured = RuleOptions.apply(rule, fixtures.options(caseName));
      problems.addAll(
          check(
              runner,
              configured,
              fixtures.violating(caseName),
              fixtures.expected(caseName),
              typeFacts));
      if (Files.isRegularFile(fixtures.passing(caseName))) {
        problems.addAll(
            check(runner, configured, fixtures.passing(caseName), List.of(), typeFacts));
      }
    }
    assertThat(problems).isEmpty();
  }

  static Stream<CatalogRow> textRows() {
    return CATALOG.rows().stream().filter(CatalogRow::isText);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("textRows")
  void textViolatingFixtureReportsExactlyTheExpectedPositionsAndPassingReportsNone(
      final CatalogRow row) throws IOException {
    final RuleDefinition rule = RULES.get(row.textId());
    assertThat(rule).as("rule definition %s", row.textId()).isNotNull();
    final RuleFixtureSet fixtures = RuleFixtureSet.ofText(RuleFixtureSet.root(), row);
    assertThat(fixtures.complete()).as("fixtures of %s", fixtures.directory()).isTrue();
    final List<String> problems = new ArrayList<>();
    problems.addAll(checkText(rule, fixtures.violating(""), fixtures.expected("")));
    problems.addAll(checkText(rule, fixtures.passing(""), List.of()));
    assertThat(problems).isEmpty();
  }

  private static List<String> checkText(
      final RuleDefinition rule, final Path file, final List<String> expected) throws IOException {
    final RuleRunResult result =
        new RuleEngine(BundledRuleMatchers.create())
            .runText(
                List.of(new TextFile(file.getFileName().toString(), Files.readString(file))),
                List.of(rule));
    final List<String> problems = new ArrayList<>();
    if (!result.executionErrors().isEmpty()) {
      problems.add(rule.id() + " has execution errors: " + result.executionErrors());
    }
    final List<String> found =
        result.findings().stream()
            .map(
                finding ->
                    (finding.line() == null ? 0 : finding.line())
                        + ":"
                        + (finding.column() == null ? 0 : finding.column()))
            .toList();
    if (!found.equals(expected)) {
      problems.add(file.getFileName() + " expected " + expected + " but found " + found);
    }
    return problems;
  }

  @Test
  void everyFixtureDirectoryBelongsToAnImplementedPair() throws IOException {
    final List<String> expected = new ArrayList<>();
    implemented().stream()
        .map(pair -> RuleFixtureSet.of(RuleFixtureSet.root(), pair.row(), pair.language()))
        .map(set -> set.directory().toString())
        .forEach(expected::add);
    textRows()
        .map(row -> RuleFixtureSet.ofText(RuleFixtureSet.root(), row).directory().toString())
        .forEach(expected::add);
    final List<String> found = new ArrayList<>();
    try (Stream<Path> languages = Files.list(RuleFixtureSet.root())) {
      for (final Path language : languages.toList()) {
        try (Stream<Path> names = Files.list(language)) {
          names.map(Path::toString).forEach(found::add);
        }
      }
    }

    assertThat(found).containsExactlyInAnyOrderElementsOf(expected);
  }

  private static List<String> check(
      final RuleFixtureRunner runner,
      final RuleDefinition rule,
      final Path file,
      final List<String> expected,
      final TypeFacts typeFacts)
      throws IOException {
    final List<AnalysisFinding> findings =
        GraphRules.isGraphRule(rule)
            ? FixtureProjects.projectFindings(runner, rule, file)
            : runner.run(
                rule,
                unitName(file),
                Files.readString(file),
                typeFacts,
                FixtureProjects.fixtureProject(file, rule));
    return FixtureComparison.problems(
        rule.id(),
        rule.languages().iterator().next(),
        file.getFileName().toString(),
        expected,
        findings);
  }

  /** The path the rule sees: the first line of {@code filename.txt} beside the fixture, if any. */
  private static String unitName(final Path file) throws IOException {
    final Path named = file.resolveSibling("filename.txt");
    return Files.isRegularFile(named)
        ? Files.readString(named).strip()
        : file.getFileName().toString();
  }

  private static List<Pair> implemented() {
    final List<Pair> pairs = new ArrayList<>();
    for (final CatalogRow row : CATALOG.rows()) {
      for (final AnalysisLanguage language : row.languages(CatalogStatus.IMPLEMENTED)) {
        pairs.add(new Pair(row, language));
      }
    }
    return pairs;
  }
}
