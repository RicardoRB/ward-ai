package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.openapi.breaking.BreakingRule;
import dev.ward.analysis.domain.openapi.breaking.BreakingRules;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the breaking-change rules must never be, and what no gate may silently skip. */
class BreakingRulesPolicyTest {
  private static final RuleCatalog CATALOG = new RuleCatalogReader().load();

  private static List<CatalogRow> rows() {
    return CATALOG.rows().stream().filter(CatalogRow::breaking).toList();
  }

  private static java.util.stream.Stream<CatalogRow> implemented() {
    return rows().stream().filter(row -> !row.languages(CatalogStatus.IMPLEMENTED).isEmpty());
  }

  @Test
  void noErrorLevelCheckIsStillPlanned() {
    assertThat(rows())
        .filteredOn(row -> row.status(AnalysisLanguage.OPENAPI).status() == CatalogStatus.PLANNED)
        .extracting(CatalogRow::name)
        .as("rows the openapi-diff and openapi-compat gates would silently skip")
        .isEmpty();
  }

  @Test
  void aBreakingRuleIsNeverALintRule() {
    final List<String> lintIds =
        CatalogRuleDefinitions.all().stream().map(RuleDefinition::id).toList();

    assertThat(BreakingRules.all().stream().map(BreakingRule::id))
        .doesNotContainAnyElementsOf(lintIds);
  }

  @Test
  void everyImplementedRuleIsInTheReference() {
    final String reference =
        String.join(
            "\n",
            RuleReference.pages(CATALOG, CatalogRuleDefinitions.all(), UpstreamLists.outOfScope())
                .values());

    assertThat(implemented().map(row -> "`" + row.idFor(AnalysisLanguage.OPENAPI) + "`"))
        .allSatisfy(id -> assertThat(reference).contains(id));
  }
}
