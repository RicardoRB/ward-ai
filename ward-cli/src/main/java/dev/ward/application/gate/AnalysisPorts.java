package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.util.List;

/**
 * What the analysis gate needs from its adapters.
 *
 * @param rules loads the rule files
 * @param matcher builds rules from their bodies
 * @param sources reads the source files
 * @param frontends the language frontends and the capabilities they provide
 */
public record AnalysisPorts(
    AnalysisRuleSource rules,
    RuleMatcher matcher,
    AnalysisSourceUnits sources,
    List<FrontendSupport> frontends) {

  /** Copies the frontends. */
  public AnalysisPorts {
    frontends = List.copyOf(frontends);
  }
}
