package dev.ward.application.gate;

import dev.ward.analysis.application.rule.UnusedDefinitionFindings;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.GraphRules;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Tells what the rules of one language read besides the unit they check. */
final class DependencyReads {
  private DependencyReads() {}

  /** Reports whether a rule of the language reads the files around its unit. */
  static boolean dependencies(final AnalysisLanguage language, final List<RuleDefinition> rules) {
    return rules.stream()
        .anyMatch(rule -> rule.covers(language) && rule.capability().readsDependencies());
  }

  /**
   * Reports whether the key of a unit holds the names of the files around it: when a rule of the
   * language reads them, and also when a rule reads type facts, which come from the whole project
   * and whose results were always invalidated with these names. A language whose only dependency
   * rules are taint rules leaves them out, since a taint rule reads the summary of the unit.
   */
  static boolean names(final AnalysisLanguage language, final List<RuleDefinition> rules) {
    return rules.stream()
        .anyMatch(
            rule ->
                rule.covers(language)
                    && (rule.capability().readsDependencyNames()
                        || rule.capability() == RuleCapability.TYPES)
        );
  }

  /** Reports whether a rule of the language reads the text of the files its unit depends on. */
  static boolean bodies(final AnalysisLanguage language, final List<RuleDefinition> rules) {
    return rules.stream()
        .anyMatch(rule -> rule.covers(language) && rule.capability().readsDependencyBodies());
  }

  /**
   * The languages whose files the dependency step must summarize: those where a selected rule reads
   * the dependencies, the graph or the summaries (the architecture and unused-definition rules).
   */
  static Set<AnalysisLanguage> summarized(final List<RuleDefinition> rules) {
    final Set<AnalysisLanguage> languages = EnumSet.noneOf(AnalysisLanguage.class);
    for (final AnalysisLanguage language : AnalysisLanguage.values()) {
      if (rules.stream().anyMatch(rule -> rule.covers(language) && readsSummaries(rule))) {
        languages.add(language);
      }
    }
    return languages;
  }

  private static boolean readsSummaries(final RuleDefinition rule) {
    return rule.capability().readsDependencies()
        || GraphRules.isGraphRule(rule)
        || UnusedDefinitionFindings.isUnusedDefinitionRule(rule);
  }
}
