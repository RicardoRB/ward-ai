package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Proves that the pinned lists are fully classified, and that the check itself can fail. */
class UpstreamCoverageTest {

  private static final String ROWS =
      """
      [[rule]]
      name = "method-length"
      category = "sizes"
      status = { java = "planned" }
      issues = { java = "#284" }
      checkstyle = ["MethodLength"]
      ruff = ["PLR0915"]
      """;

  @Test
  void everyPinnedRuleOfEveryToolIsClassified() {
    final RuleCatalog catalog = new RuleCatalogReader().load();

    final List<String> problems =
        UpstreamCoverage.problems(catalog, UpstreamLists.pinned(), UpstreamLists.outOfScope());

    assertThat(problems).isEmpty();
  }

  @Test
  void everyToolOfTheRoadmapsHasAPinnedList() {
    assertThat(UpstreamLists.toolsWithoutList()).isEmpty();
  }

  @Test
  void everyPinnedListNamesItsGeneratingCommand() {
    for (final String tool : UpstreamLists.pinned().keySet()) {
      assertThat(UpstreamLists.header(tool))
          .as("header of %s-%s", tool, UpstreamLists.version(tool))
          .anyMatch(line -> line.contains("Generated") || line.contains("generated"));
    }
  }

  @Test
  void aCheckstyleCheckThatIsNeitherMappedNorExcludedFailsAndIsNamed() throws IOException {
    final List<String> problems =
        UpstreamCoverage.problems(
            catalog(ROWS), Map.of("checkstyle", Set.of("MethodLength", "NewCheck")), Map.of());

    assertThat(problems)
        .containsExactly("checkstyle rule 'NewCheck' is neither in a catalog row nor out of scope");
  }

  @Test
  void aRuffRuleMissingFromThePinnedListFailsAndNamesTheRowAndTheRule() throws IOException {
    final List<String> problems =
        UpstreamCoverage.problems(catalog(ROWS), Map.of("ruff", Set.of("PLR0913")), Map.of());

    assertThat(problems)
        .contains(
            "row 'method-length' names the ruff rule 'PLR0915', which is not in the pinned list")
        .contains("ruff rule 'PLR0913' is neither in a catalog row nor out of scope");
  }

  @Test
  void aRuleInARowAndInTheOutOfScopeFileFails() throws IOException {
    final List<String> problems =
        UpstreamCoverage.problems(
            catalog(ROWS),
            Map.of("checkstyle", Set.of("MethodLength")),
            Map.of("checkstyle", Map.of("MethodLength", "formatting")));

    assertThat(problems)
        .containsExactly("checkstyle rule 'MethodLength' is in a catalog row and out of scope");
  }

  @Test
  void anOutOfScopeRuleNeedsAReasonAndAPinnedName() throws IOException {
    final List<String> problems =
        UpstreamCoverage.problems(
            catalog(ROWS),
            Map.of("checkstyle", Set.of("MethodLength")),
            Map.of("checkstyle", Map.of("Gone", " ")));

    assertThat(problems)
        .containsExactly(
            "checkstyle out-of-scope rule 'Gone' is not in the pinned list",
            "checkstyle out-of-scope rule 'Gone' has no reason");
  }

  private static RuleCatalog catalog(final String rows) throws IOException {
    final List<CatalogRow> parsed =
        new RuleCatalogReader().parse("test.toml", new StringReader(rows));
    return new RuleCatalog(parsed);
  }
}
