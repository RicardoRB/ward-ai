package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.LayerDependencyRule;
import dev.ward.analysis.domain.rule.LayerRuleBody;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;

/**
 * Compiles {@code ward:architecture/layers}, whose body carries the boundaries of the gate that
 * configured it. Every other rule goes to the matcher it wraps.
 */
public final class LayerRuleMatcher implements RuleMatcher {

  private final RuleMatcher next;

  /** Creates the matcher that compiles the layer rule and passes other rules to {@code next}. */
  public LayerRuleMatcher(final RuleMatcher next) {
    this.next = next;
  }

  @Override
  public CompiledRule compile(final RuleDefinition definition) {
    if (!LayerDependencyRule.ID.equals(definition.id())) {
      return next.compile(definition);
    }
    try {
      return new LayerDependencyRule(definition, LayerRuleBody.decode(definition.body()));
    } catch (final IllegalArgumentException exception) {
      throw new AnalysisFailedException("the layer boundaries are malformed", exception);
    }
  }
}
