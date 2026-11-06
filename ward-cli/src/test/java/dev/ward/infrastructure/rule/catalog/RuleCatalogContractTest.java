package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The contract between the catalog and the rules that ship: every rule definition is in exactly one
 * row, every implemented pair has what it needs, and every planned pair names an issue of the
 * roadmaps.
 */
class RuleCatalogContractTest {

  private static final int FIRST_STYLE_ISSUE = 283;
  private static final int LAST_STYLE_ISSUE = 301;
  private static final int FIRST_TOOL_ISSUE = 308;
  private static final int LAST_TOOL_ISSUE = 341;

  private final RuleCatalog catalog = new RuleCatalogReader().load();
  private final List<RuleDefinition> definitions = CatalogRuleDefinitions.all();

  @Test
  void everyRuleDefinitionThatShipsBelongsToExactlyOneImplementedRow() {
    final Map<String, List<String>> owners =
        catalog.rows().stream()
            .flatMap(
                row ->
                    row.languages(CatalogStatus.IMPLEMENTED).stream()
                        .map(language -> Map.entry(row.idFor(language), row.name())))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

    for (final RuleDefinition rule : definitions) {
      assertThat(owners.get(rule.id())).as("rows of %s", rule.id()).hasSize(1);
    }
    assertThat(owners.keySet())
        .as("implemented pairs without a rule definition")
        .containsExactlyInAnyOrderElementsOf(definitions.stream().map(RuleDefinition::id).toList());
  }

  @Test
  void everyImplementedPairHasACategoryPresetsFixturesAndAReferenceEntry() {
    final String reference =
        RuleReference.pages(catalog, definitions, UpstreamLists.outOfScope())
            .get(RuleReference.INDEX);
    final Map<String, RuleDefinition> byId = new HashMap<>();
    definitions.forEach(rule -> byId.put(rule.id(), rule));
    for (final CatalogRow row : catalog.rows()) {
      for (final AnalysisLanguage language : row.languages(CatalogStatus.IMPLEMENTED)) {
        final RuleDefinition rule = byId.get(row.idFor(language));
        assertThat(rule.category()).as("category of %s", rule.id()).isNotBlank();
        assertThat(row.category()).as("category of row %s", row.name()).isIn("", rule.category());
        assertThat(rule.presets())
            .as("presets of %s", rule.id())
            .containsAnyOf(RulePreset.STANDARD, RulePreset.STRICT);
        assertThat(RuleFixtureSet.of(RuleFixtureSet.root(), row, language).complete())
            .as("fixtures of %s", rule.id())
            .isTrue();
        assertThat(reference)
            .as("reference entry of %s", rule.id())
            .contains("`" + rule.id() + "`");
      }
    }
  }

  @Test
  void everyPlannedPairNamesAnIssueOfARoadmap() {
    final List<String> problems = new ArrayList<>();
    for (final CatalogRow row : catalog.rows()) {
      for (final AnalysisLanguage language : row.languages(CatalogStatus.PLANNED)) {
        final LanguageStatus status = row.status(language);
        if (!roadmapIssue(status.issue())) {
          problems.add(row.name() + " in " + language + " names " + status.issue());
        }
      }
    }

    assertThat(problems).isEmpty();
  }

  @Test
  void aPlannedPairHasNoRuleDefinitionYet() {
    final List<String> ids = definitions.stream().map(RuleDefinition::id).toList();
    for (final CatalogRow row : catalog.rows()) {
      for (final AnalysisLanguage language : row.languages(CatalogStatus.PLANNED)) {
        assertThat(ids)
            .as("%s is planned but ships", row.idFor(language))
            .doesNotContain(row.idFor(language));
      }
    }
  }

  @Test
  void everyRowWithoutAnImplementedRuleHasACategoryAndALanguage() {
    for (final CatalogRow row : catalog.rows()) {
      if (row.languages(CatalogStatus.IMPLEMENTED).isEmpty()) {
        assertThat(row.category()).as("category of %s", row.name()).isNotBlank();
        assertThat(row.languages(CatalogStatus.PLANNED))
            .as("planned languages of %s", row.name())
            .isNotEmpty();
      }
    }
  }

  @Test
  void aPlannedRowGeneratedFromACheckstyleCheckGoesToAStyleIssue() {
    for (final CatalogRow row : catalog.rows()) {
      if (row.counterparts().containsKey("checkstyle")) {
        for (final AnalysisLanguage language : row.languages(CatalogStatus.PLANNED)) {
          final int issue = Integer.parseInt(row.status(language).issue().substring(1));
          assertThat(issue)
              .as("issue of %s in %s", row.name(), language)
              .isBetween(FIRST_STYLE_ISSUE, LAST_STYLE_ISSUE);
        }
      }
    }
  }

  @Test
  void theCatalogCoversEveryEngineLanguageSomewhere() {
    for (final AnalysisLanguage language : AnalysisLanguage.values()) {
      final boolean covered =
          catalog.rows().stream()
              .anyMatch(
                  row ->
                      !row.languages(CatalogStatus.IMPLEMENTED).isEmpty()
                          && row.languages(CatalogStatus.IMPLEMENTED).contains(language));
      assertThat(covered).as("an implemented rule in %s", language).isTrue();
    }
  }

  private static boolean roadmapIssue(final String issue) {
    final int number = Integer.parseInt(issue.substring(1));
    return number >= FIRST_STYLE_ISSUE && number <= LAST_STYLE_ISSUE
        || number >= FIRST_TOOL_ISSUE && number <= LAST_TOOL_ISSUE;
  }
}
