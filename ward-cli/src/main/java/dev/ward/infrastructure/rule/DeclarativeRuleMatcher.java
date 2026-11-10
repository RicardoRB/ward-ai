package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.CheckProvider;
import dev.ward.analysis.application.rule.CheckRegistry;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.check.CheckedRule;
import java.util.Set;
import javax.inject.Inject;

/**
 * Compiles a rule whose body is a TOML description of one check, such as {@code check = "lines"}.
 * The body format is owned here, so the engine never reads it.
 */
public final class DeclarativeRuleMatcher implements RuleMatcher {

  private final DeclarativeChecks checks;

  /** Creates the matcher over the checks of every bundled language module. */
  public DeclarativeRuleMatcher() {
    this(Set.copyOf(BundledLanguages.checkProviders()));
  }

  /** Creates the matcher over the checks of {@code providers}. */
  @Inject
  public DeclarativeRuleMatcher(final Set<CheckProvider> providers) {
    this.checks = new DeclarativeChecks(new CheckRegistry(providers));
  }

  @Override
  public CompiledRule compile(final RuleDefinition definition) {
    try {
      return new CheckedRule(definition, checks.build(definition.body(), definition.languages()));
    } catch (final RuleConfigurationException exception) {
      throw new AnalysisFailedException(exception.getMessage(), exception);
    }
  }
}
