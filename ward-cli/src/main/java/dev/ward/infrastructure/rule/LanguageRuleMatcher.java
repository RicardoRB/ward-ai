package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.util.Set;

/**
 * Sends each rule to the matcher of its language. TypeScript and JavaScript rules go to the
 * structural matcher, which names the metric in the id; a rule it cannot use, such as a taint
 * check, goes to the shared matcher. Every other language uses the shared matcher.
 */
public final class LanguageRuleMatcher implements RuleMatcher {

  private static final Set<AnalysisLanguage> STRUCTURAL =
      Set.of(AnalysisLanguage.TYPESCRIPT, AnalysisLanguage.JAVASCRIPT);

  private final RuleMatcher structural;
  private final RuleMatcher shared;

  /** Creates the dispatcher over the structural matcher and the shared matcher. */
  public LanguageRuleMatcher(final RuleMatcher structural, final RuleMatcher shared) {
    this.structural = structural;
    this.shared = shared;
  }

  @Override
  public CompiledRule compile(final RuleDefinition definition) {
    if (!isStructural(definition)) {
      return shared.compile(definition);
    }
    try {
      return structural.compile(definition);
    } catch (final AnalysisFailedException structuralFailure) {
      try {
        return shared.compile(definition);
      } catch (final AnalysisFailedException sharedFailure) {
        structuralFailure.addSuppressed(sharedFailure);
        throw structuralFailure;
      }
    }
  }

  private static boolean isStructural(final RuleDefinition definition) {
    return definition.languages().stream().allMatch(STRUCTURAL::contains);
  }
}
