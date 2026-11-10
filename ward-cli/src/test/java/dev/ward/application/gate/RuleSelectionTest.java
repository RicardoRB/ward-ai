package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The selection that {@code ward rules} shows is the one the gate runs. */
class RuleSelectionTest {
  private static final String STANDARD_RULE = "ward:java/standard-rule";
  private static final String STRICT_RULE = "ward:java/strict-rule";

  @Test
  void presetIncludeAndExcludeDecideTheSelectedIds() {
    final List<RuleDefinition> catalog =
        List.of(rule(STANDARD_RULE, RulePreset.STANDARD), rule(STRICT_RULE, RulePreset.STRICT));

    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(
                "strict", List.of(), List.of(), List.of(), List.of(STANDARD_RULE)),
            catalog);

    assertThat(result.ids()).containsExactly(STRICT_RULE);
    assertThat(result.problems()).isEmpty();
  }

  @Test
  void anUnknownIdIsReportedAsAProblem() {
    final RuleSelection.Result result =
        RuleSelection.selected(
            new RuleConfigFile.Select(
                null, List.of(), List.of(), List.of("ward:java/missing"), List.of()),
            List.of(rule(STANDARD_RULE, RulePreset.STANDARD)));

    assertThat(result.ids()).containsExactly(STANDARD_RULE);
    assertThat(result.problems()).anyMatch(problem -> problem.contains("ward:java/missing"));
  }

  private static RuleDefinition rule(final String id, final RulePreset preset) {
    return new RuleDefinition(
        id,
        Set.of(AnalysisLanguage.JAVA),
        RuleCapability.SYNTAX,
        AnalysisSeverity.ERROR,
        "check = \"x\"",
        "sizes",
        Set.of(preset),
        List.of(),
        0);
  }
}
