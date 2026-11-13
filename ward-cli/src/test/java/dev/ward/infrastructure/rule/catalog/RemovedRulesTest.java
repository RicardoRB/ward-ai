package dev.ward.infrastructure.rule.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.application.gate.RuleConfigFile;
import dev.ward.application.gate.RuleSelection;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A rule id that Ward removed is unknown to the shipped catalog, like any other missing id. */
class RemovedRulesTest {

  private static final String REMOVED_JAVA = "java.use-of-md5";
  private static final String REMOVED_JAVASCRIPT = "javascript.tainted-html-string";

  @Test
  void aRulesFileThatSelectsARemovedRuleIsReportedAsUnknown() {
    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(
                null, List.of(), List.of(), List.of(REMOVED_JAVA, REMOVED_JAVASCRIPT), List.of()),
            CatalogRuleDefinitions.shipped());

    assertThat(result.problems())
        .anyMatch(problem -> problem.contains("unknown rule \"" + REMOVED_JAVA + "\""))
        .anyMatch(problem -> problem.contains("unknown rule \"" + REMOVED_JAVASCRIPT + "\""));
  }

  @Test
  void theStandardPresetSelectsEveryShippedStandardRuleWithoutProblems() {
    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select("standard", List.of(), List.of(), List.of(), List.of()),
            CatalogRuleDefinitions.shipped());

    assertThat(result.problems()).isEmpty();
    assertThat(result.ids())
        .containsAll(
            CatalogRuleDefinitions.shipped().stream()
                .filter(rule -> rule.presets().contains(RulePreset.STANDARD))
                .map(RuleDefinition::id)
                .toList());
  }
}
