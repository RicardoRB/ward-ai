package dev.ward.application.gate;

import dev.ward.analysis.application.rule.DependencySnapshot;
import dev.ward.analysis.application.rule.FrontendSupport;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.AnalysisCache;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The cache keys of the units of one run: the rule set of each language and the unit's inputs. */
final class UnitKeys {
  private final String engineVersion;
  private final List<RuleDefinition> rules;
  private final DependencySnapshot snapshot;
  private final FlowDigests digests;
  private final Map<AnalysisLanguage, String> ruleSets = new EnumMap<>(AnalysisLanguage.class);

  UnitKeys(
      final String engineVersion,
      final List<RuleDefinition> rules,
      final DependencySnapshot snapshot,
      final AnalysisCache cache,
      final List<FrontendSupport> frontends) {
    this.engineVersion = engineVersion;
    this.rules = List.copyOf(rules);
    this.snapshot = snapshot;
    this.digests = new FlowDigests(cache, engineVersion, frontends);
  }

  /**
   * The key of a unit's result. A language whose rules read dependencies keys each unit on the
   * files around it too, and one that follows calls into other files on their flow digests.
   */
  String of(final SourceUnit unit) {
    final String ruleSet = ruleSets.computeIfAbsent(unit.language(), this::ruleSet);
    if (!DependencyReads.dependencies(unit.language(), rules)) {
      return AnalysisCacheKey.unit(ruleSet, unit);
    }
    final List<FlowDigests.Related> related =
        DependencyReads.bodies(unit.language(), rules)
            ? digests.of(snapshot.closureOf(unit.path()))
            : List.of();
    return AnalysisCacheKey.unit(ruleSet, unit, snapshot.view(unit.path()), related);
  }

  private String ruleSet(final AnalysisLanguage language) {
    return AnalysisCacheKey.ruleSet(
        engineVersion, rules.stream().filter(rule -> rule.covers(language)).toList());
  }
}
