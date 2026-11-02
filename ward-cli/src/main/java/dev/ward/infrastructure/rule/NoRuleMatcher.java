package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import javax.inject.Inject;

/**
 * shortcut: no rule body format is chosen until the parser runtime decision (#185) lands; a rule
 * then fails to compile and is reported as an execution error, never as passed. Replace it with the
 * real matcher adapter when that decision is made.
 */
public final class NoRuleMatcher implements RuleMatcher {

  /** Creates the matcher that compiles no rules. */
  @Inject
  public NoRuleMatcher() {}

  @Override
  public CompiledRule compile(final RuleDefinition definition) {
    throw new AnalysisFailedException("no rule matcher is available");
  }
}
