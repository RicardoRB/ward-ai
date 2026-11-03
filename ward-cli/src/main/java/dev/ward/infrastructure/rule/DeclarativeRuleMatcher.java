package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.check.CheckedRule;
import javax.inject.Inject;

/**
 * Compiles a rule whose body is a TOML description of one check, such as {@code check = "lines"}.
 * The body format is owned here, so the engine never reads it.
 */
public final class DeclarativeRuleMatcher implements RuleMatcher {

  /** Creates the matcher. */
  @Inject
  public DeclarativeRuleMatcher() {}

  @Override
  public CompiledRule compile(final RuleDefinition definition) {
    try {
      return new CheckedRule(definition, DeclarativeChecks.build(definition.body()));
    } catch (final RuleConfigurationException exception) {
      throw new AnalysisFailedException(exception.getMessage(), exception);
    }
  }
}
