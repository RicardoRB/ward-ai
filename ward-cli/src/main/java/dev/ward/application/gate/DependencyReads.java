package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;

/** Tells what the rules of one language read besides the unit they check. */
final class DependencyReads {

  private DependencyReads() {}

  /** Reports whether a rule of the language reads the files around its unit. */
  static boolean dependencies(final AnalysisLanguage language, final List<RuleDefinition> rules) {
    return rules.stream()
        .anyMatch(rule -> rule.covers(language) && rule.capability().readsDependencies());
  }

  /** Reports whether a rule of the language reads the text of the files its unit depends on. */
  static boolean bodies(final AnalysisLanguage language, final List<RuleDefinition> rules) {
    return rules.stream()
        .anyMatch(rule -> rule.covers(language) && rule.capability().readsDependencyBodies());
  }
}
