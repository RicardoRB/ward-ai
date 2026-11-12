package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.openapi.breaking.BreakingFinding;
import dev.ward.analysis.domain.openapi.breaking.BreakingRule;
import dev.ward.analysis.domain.openapi.breaking.BreakingRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The catalog contract of the breaking-change rules, the counterpart of the single-document tests
 * for lint rules: an implemented row has a rule, a violating pair whose findings and positions are
 * as expected and whose operations are those of a real oasdiff run, a passing pair that yields
 * nothing, and an entry in the reference; a row that is not implemented says why.
 */
class BreakingRulesCatalogTest {

  private static final String ISSUE = "#523";
  private static final String ID_PREFIX = "ward:openapi/";
  private static final RuleCatalog CATALOG = new RuleCatalogReader().load();

  static Stream<CatalogRow> implemented() {
    return rows().stream().filter(row -> !row.languages(CatalogStatus.IMPLEMENTED).isEmpty());
  }

  static Stream<String> rowNames() {
    return implemented().map(CatalogRow::name);
  }

  @Test
  void everyImplementedRowHasExactlyOneRuleAndEveryRuleHasARow() {
    final List<String> rows =
        implemented().map(row -> row.idFor(AnalysisLanguage.OPENAPI)).sorted().toList();
    final List<String> rules = BreakingRules.all().stream().map(BreakingRule::id).sorted().toList();

    assertThat(rules).containsExactlyElementsOf(rows);
  }

  @Test
  void aRowThatIsNotImplementedHasACategoryAndAReasonOrAnIssue() {
    for (final CatalogRow row : rows()) {
      assertThat(row.category()).as("category of %s", row.name()).isNotBlank();
      final LanguageStatus status = row.status(AnalysisLanguage.OPENAPI);
      if (status.status() == CatalogStatus.PLANNED) {
        assertThat(status.issue()).as("issue of %s", row.name()).isEqualTo(ISSUE);
      } else if (status.status() == CatalogStatus.NOT_APPLICABLE) {
        assertThat(status.reason()).as("reason of %s", row.name()).isNotBlank();
      }
    }
  }

  @Test
  void everyFixtureDirectoryBelongsToAnImplementedRow() throws IOException {
    final List<String> expected = implemented().map(CatalogRow::name).sorted().toList();
    try (Stream<Path> directories = Files.list(BreakingFixtures.root())) {
      assertThat(directories.map(path -> path.getFileName().toString()).sorted().toList())
          .isEqualTo(expected);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rowNames")
  void theViolatingPairYieldsTheExpectedFindingsOfThatRuleOnly(final String name)
      throws IOException {
    final Path directory = BreakingFixtures.root().resolve(name);
    final String id = ID_PREFIX + name;
    final List<BreakingFinding> own = BreakingFixtures.findings(directory, "revision.yaml", id);

    assertThat(own.stream().map(BreakingFixtures::position).toList())
        .as("positions of %s", id)
        .containsExactlyElementsOf(BreakingFixtures.lines(directory.resolve("expected.txt")));
    assertThat(own.stream().map(BreakingFixtures::operation).sorted().toList())
        .as("operations of %s against the oasdiff run", id)
        .containsExactlyElementsOf(BreakingFixtures.oasdiff(directory, name));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rowNames")
  void thePassingPairAndTheBaseAgainstItselfYieldNothing(final String name) {
    final Path directory = BreakingFixtures.root().resolve(name);

    assertThat(BreakingFixtures.findings(directory, "passing.yaml", "")).isEmpty();
    assertThat(BreakingFixtures.findings(directory, BreakingFixtures.BASE, "")).isEmpty();
  }

  private static List<CatalogRow> rows() {
    return CATALOG.rows().stream().filter(CatalogRow::breaking).toList();
  }
}
