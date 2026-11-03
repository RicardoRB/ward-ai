package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleMatcher;
import java.util.Set;

/**
 * Sends each rule to the matcher of its language. TypeScript and JavaScript rules name their metric
 * in the id, so they go to the structural matcher; every other language uses the shared matcher.
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
    return matcherFor(definition).compile(definition);
  }

  private RuleMatcher matcherFor(final RuleDefinition definition) {
    return STRUCTURAL.contains(definition.language()) ? structural : shared;
  }
}
