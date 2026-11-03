package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.metric.MetricMatcher;

/**
 * Compiles a rule in the format its body is written in: a declarative check (Java rules), or a
 * metric rule (Python rules). A body that is neither is reported with the declarative error, since
 * a declarative body is the one a rule author writes as TOML.
 */
public final class CompositeRuleMatcher implements RuleMatcher {

  private final RuleMatcher declarative = new DeclarativeRuleMatcher();
  private final RuleMatcher metric = new MetricMatcher();

  @Override
  public CompiledRule compile(final RuleDefinition definition) {
    try {
      return declarative.compile(definition);
    } catch (final AnalysisFailedException declarativeFailure) {
      try {
        return metric.compile(definition);
      } catch (final AnalysisFailedException metricFailure) {
        throw declarativeFailure;
      }
    }
  }
}
