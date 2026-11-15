package dev.ward.analysis.application.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleExecutionError;
import dev.ward.analysis.domain.rule.RuleMatcher;
import dev.ward.analysis.domain.rule.RuleNotRun;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A rule compiled once, up front, with the languages it runs on, so the type needs of all rules are
 * known before the first unit is read. The rule is empty when it was not run or failed to compile.
 *
 * @param rule the compiled rule
 * @param runnable the languages whose frontend provides the rule's capability
 */
record PreparedRule(Optional<CompiledRule> rule, List<AnalysisLanguage> runnable) {

  /** Copies the languages. */
  PreparedRule {
    runnable = List.copyOf(runnable);
  }

  /**
   * Prepares {@code rules}; a rule a frontend cannot serve is recorded as not run. A {@code TEXT}
   * rule needs no frontend and runs through {@link RuleEngine#runText}, so it is left out.
   */
  static List<PreparedRule> all(
      final List<RuleDefinition> rules,
      final Map<AnalysisLanguage, FrontendSupport> support,
      final RuleMatcher matcher,
      final RuleRunCollector collector) {
    return rules.stream()
        .filter(rule -> rule.capability() != RuleCapability.TEXT)
        .map(rule -> of(rule, support, matcher, collector))
        .toList();
  }

  /** The type-fact needs of the prepared {@code TYPES} rules, by language. */
  static Map<AnalysisLanguage, List<FactNeed>> needs(final List<PreparedRule> prepared) {
    final Map<AnalysisLanguage, List<FactNeed>> needs = new EnumMap<>(AnalysisLanguage.class);
    for (final PreparedRule item : prepared) {
      item.rule()
          .filter(rule -> rule.definition().capability() == RuleCapability.TYPES)
          .ifPresent(
              rule ->
                  rule.definition()
                      .languages()
                      .forEach(
                          language ->
                              needs
                                  .computeIfAbsent(language, key -> new ArrayList<>())
                                  .add(rule.factNeed())));
    }
    return needs;
  }

  private static PreparedRule of(
      final RuleDefinition definition,
      final Map<AnalysisLanguage, FrontendSupport> support,
      final RuleMatcher matcher,
      final RuleRunCollector collector) {
    final List<AnalysisLanguage> runnable =
        definition.languages().stream()
            .filter(language -> provides(support.get(language), definition.capability()))
            .toList();
    if (!runnable.containsAll(definition.languages())) {
      collector.notRun(new RuleNotRun(definition.id(), definition.capability()));
      return new PreparedRule(Optional.empty(), List.of());
    }
    return new PreparedRule(compile(definition, matcher, collector), runnable);
  }

  private static boolean provides(final FrontendSupport frontend, final RuleCapability capability) {
    return frontend != null && frontend.capabilities().contains(capability);
  }

  private static Optional<CompiledRule> compile(
      final RuleDefinition definition,
      final RuleMatcher matcher,
      final RuleRunCollector collector) {
    try {
      return Optional.of(matcher.compile(definition));
    } catch (final AnalysisFailedException exception) {
      collector.recordError(
          new RuleExecutionError(definition.id(), null, "the rule could not be compiled"));
      return Optional.empty();
    }
  }
}
