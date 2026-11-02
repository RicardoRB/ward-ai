package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;

/**
 * The rules that loaded, and one message per file that did not.
 *
 * @param rules the usable rules, in file order
 * @param configurationErrors one message per skipped file, each naming the file
 */
public record AnalysisRuleLoad(List<RuleDefinition> rules, List<String> configurationErrors) {

  /** Copies both lists. */
  public AnalysisRuleLoad {
    rules = List.copyOf(rules);
    configurationErrors = List.copyOf(configurationErrors);
  }
}
