package dev.ward.application.gate;

import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.CompiledRule;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The complexity rule of each language that the {@code crap} gate scores, compiled, with the
 * frontend that parses its files.
 *
 * @param rules the compiled complexity rule of each language
 * @param frontends the frontend of each of those languages
 */
record ComplexityRules(
    Map<AnalysisLanguage, CompiledRule> rules, Map<AnalysisLanguage, FrontendSupport> frontends) {

  private static final Map<AnalysisLanguage, String> IDS =
      Map.of(
          AnalysisLanguage.JAVA, "ward:java/cyclomatic-complexity",
          AnalysisLanguage.KOTLIN, "ward:kotlin/cyclomatic-complexity",
          AnalysisLanguage.TYPESCRIPT, "ward:typescript/complexity",
          AnalysisLanguage.JAVASCRIPT, "ward:javascript/complexity",
          AnalysisLanguage.PYTHON, "ward:python/complexity",
          AnalysisLanguage.SWIFT, "ward:swift/complexity");

  /** Copies the maps. */
  ComplexityRules {
    rules = Map.copyOf(rules);
    frontends = Map.copyOf(frontends);
  }

  /**
   * Compiles the complexity rule of each language that has one and a frontend. A rule's selection
   * and limit in {@code ward-rules.toml} do not matter: only the value it measures is used.
   */
  static ComplexityRules load(final AnalysisPorts analysis) {
    final Map<String, RuleDefinition> byId =
        analysis.rules().load().rules().stream()
            .collect(Collectors.toMap(RuleDefinition::id, rule -> rule, (first, second) -> first));
    final Map<AnalysisLanguage, FrontendSupport> frontends =
        analysis.frontends().stream()
            .filter(support -> IDS.containsKey(support.language()))
            .collect(
                Collectors.toMap(
                    FrontendSupport::language, support -> support, (first, second) -> first));
    final Map<AnalysisLanguage, CompiledRule> rules = new EnumMap<>(AnalysisLanguage.class);
    final Map<AnalysisLanguage, FrontendSupport> usable = new EnumMap<>(AnalysisLanguage.class);
    frontends.forEach(
        (language, support) -> {
          final RuleDefinition definition = byId.get(IDS.get(language));
          if (definition != null) {
            rules.put(language, analysis.matcher().compile(definition));
            usable.put(language, support);
          }
        });
    return new ComplexityRules(rules, usable);
  }
}
