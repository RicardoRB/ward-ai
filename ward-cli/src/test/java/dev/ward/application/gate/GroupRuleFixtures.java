package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.List;
import java.util.Set;

/** Rule builders shared by the group selection tests. */
final class GroupRuleFixtures {

  private GroupRuleFixtures() {}

  static RuleDefinition categorised(
      final String id, final AnalysisLanguage language, final String category) {
    return withPresets(id, language, category, Set.of(RulePreset.STANDARD));
  }

  static RuleDefinition withPresets(
      final String id, final String category, final RulePreset preset) {
    return withPresets(id, AnalysisLanguage.JAVA, category, Set.of(preset));
  }

  static RuleDefinition withPresets(
      final String id,
      final AnalysisLanguage language,
      final String category,
      final Set<RulePreset> presets) {
    return new RuleDefinition(
        id,
        Set.of(language),
        RuleCapability.SYNTAX,
        AnalysisSeverity.ERROR,
        "check = \"x\"",
        category,
        presets,
        List.of(),
        0);
  }
}
