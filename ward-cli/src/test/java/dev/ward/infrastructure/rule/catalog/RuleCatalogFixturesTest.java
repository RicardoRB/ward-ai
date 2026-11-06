package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.catalog.CatalogRow;
import dev.ward.analysis.domain.rule.catalog.CatalogStatus;
import dev.ward.analysis.domain.rule.catalog.RuleCatalog;
import dev.ward.infrastructure.rule.ClasspathRuleSource;
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
      new ClasspathRuleSource()
          .load().rules().stream().collect(Collectors.toMap(RuleDefinition::id, rule -> rule));

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
    assertThat(FixtureTypeFacts.problem(fixtures.typeFacts())).isEmpty();
    final RuleFixtureRunner runner = new RuleFixtureRunner();
    final List<String> problems = new ArrayList<>();
    for (final String caseName : fixtures.cases()) {
      problems.addAll(
          check(runner, rule, fixtures.violating(caseName), fixtures.expected(caseName)));
      if (Files.isRegularFile(fixtures.passing(caseName))) {
        problems.addAll(check(runner, rule, fixtures.passing(caseName), List.of()));
      }
    }
    assertThat(problems).isEmpty();
  }

  @Test
  void everyFixtureDirectoryBelongsToAnImplementedPair() throws IOException {
    final List<String> expected =
        implemented().stream()
            .map(pair -> RuleFixtureSet.of(RuleFixtureSet.root(), pair.row(), pair.language()))
            .map(set -> set.directory().toString())
            .toList();
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
      final List<String> expected)
      throws IOException {
    final List<AnalysisFinding> findings =
        runner.run(rule, file.getFileName().toString(), Files.readString(file));
    return FixtureComparison.problems(
        rule.id(),
        rule.languages().iterator().next(),
        file.getFileName().toString(),
        expected,
        findings);
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
