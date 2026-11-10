package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;

/** Builders for the units and rule definitions that the engine tests run over. */
final class RuleEngineFixtures {
  private RuleEngineFixtures() {}

  static SourceUnit unit(final String path) {
    return new SourceUnit(path, AnalysisLanguage.JAVA, "class X {}");
  }

  static RuleDefinition rule(final String id, final RuleCapability capability) {
    return new RuleDefinition(
        id, AnalysisLanguage.JAVA, capability, AnalysisSeverity.WARNING, "body");
  }
}
