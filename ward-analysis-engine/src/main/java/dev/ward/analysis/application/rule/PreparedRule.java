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
import dev.ward.analysis.domain.rule.check.CheckedRule;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
      final Map<AnalysisLanguage,
      FrontendSupport> support,
      final RuleMatcher matcher,
      final RuleRunCollector collector
  ) {
    return rules.stream()
        .filter(rule -> rule.capability() != RuleCapability.TEXT)
        .map(rule -> of(rule, support, matcher, collector))
        .toList();
  }

  /** The type-fact needs of the prepared {@code TYPES} rules, by language. */
  static Map<AnalysisLanguage, List<FactNeed>> needs(final List<PreparedRule> prepared) {
    return prepared.stream()
        .flatMap(item -> item.rule().stream())
        .filter(rule -> rule.definition().capability() == RuleCapability.TYPES)
        .flatMap(
            rule ->
                rule.definition().languages().stream()
                    .map(language -> Map.entry(language, rule.factNeed()))
        )
        .collect(
            Collectors.groupingBy(
                Map.Entry::getKey,
                () -> new EnumMap<>(AnalysisLanguage.class),
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())
            )
        );
  }

  /** The node kinds that the prepared rules read from the shared node index. */
  static Set<String> nodeKinds(final List<PreparedRule> prepared) {
    return prepared.stream()
        .flatMap(item -> item.rule().stream())
        .flatMap(rule -> kindsOf(rule).stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  /** The node kinds {@code rule} reads from the shared node index; none for a rule of no check. */
  static Set<String> kindsOf(final CompiledRule rule) {
    return rule instanceof CheckedRule checked ? checked.getCheck().nodeKinds() : Set.of();
  }

  private static PreparedRule of(
      final RuleDefinition definition,
      final Map<AnalysisLanguage,
      FrontendSupport> support,
      final RuleMatcher matcher,
      final RuleRunCollector collector
  ) {
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
      final RuleRunCollector collector
  ) {
    try {
      return Optional.of(matcher.compile(definition));
    } catch (final AnalysisFailedException exception) {
      collector.recordError(
          new RuleExecutionError(definition.id(), null, "the rule could not be compiled")
      );
      return Optional.empty();
    }
  }
}
