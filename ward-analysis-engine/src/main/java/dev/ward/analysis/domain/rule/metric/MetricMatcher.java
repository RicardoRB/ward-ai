package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;

/**
 * Builds metric rules from their bodies. The measurements are shared by every language: a rule
 * names the grammar kinds it applies to, and the body keys say where the names and parameters are.
 */
public final class MetricMatcher implements RuleMatcher {

  @Override
  public CompiledRule compile(final RuleDefinition definition) {
    return new MetricCompiledRule(definition, MetricRuleBody.parse(definition.body()));
  }
}
