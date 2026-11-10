package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.rule.LayerBoundary;
import dev.ward.analysis.domain.rule.LayerDependencyRule;
import dev.ward.analysis.domain.rule.LayerRuleBody;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.domain.init.ArchitectureLayers;
import java.util.List;

/**
 * Builds the {@code ward:architecture/layers} rule of a check from its boundaries. The rule covers
 * every language whose dependencies the engine resolves, and the boundaries travel in its body.
 */
final class LayerRules {

  private LayerRules() {}

  /** Returns the rule that enforces {@code boundaries}. */
  static RuleDefinition definition(final List<LayerBoundary> boundaries) {
    return new RuleDefinition(
        LayerDependencyRule.ID,
        ArchitectureLayers.ENGINE_LANGUAGES,
        RuleCapability.SYMBOLS,
        AnalysisSeverity.ERROR,
        LayerRuleBody.encode(boundaries));
  }
}
