package dev.ward.application.gate;

import dev.ward.analysis.application.rule.DependencySnapshot;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.DependencyView;
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
  private final SummaryDigests summaries;
  private final Map<AnalysisLanguage, String> ruleSets = new EnumMap<>(AnalysisLanguage.class);

  UnitKeys(
      final String engineVersion,
      final List<RuleDefinition> rules,
      final DependencySnapshot snapshot,
      final SummaryDigests summaries
  ) {
    this.engineVersion = engineVersion;
    this.rules = List.copyOf(rules);
    this.snapshot = snapshot;
    this.digests = summaries.flows();
    this.summaries = summaries;
  }

  /**
   * The key of a unit's result. A language whose rules read dependencies keys each unit on the
   * files around it too, and one that follows calls into other files on the taint summary of the
   * unit, see {@link SummaryDigests}, or on their flow digests when a rule gives no summary.
   */
  String of(final SourceUnit unit) {
    final String ruleSet = ruleSets.computeIfAbsent(unit.language(), this::ruleSet);
    if (!DependencyReads.dependencies(unit.language(), rules)) {
      return AnalysisCacheKey.unit(ruleSet, unit);
    }
    final List<FlowDigests.Related> related =
        DependencyReads.bodies(unit.language(), rules)
            ? related(unit, ruleSet)
            : List.of();
    final DependencyView view =
        DependencyReads.names(unit.language(), rules)
            ? snapshot.view(unit.path())
            : DependencyView.NONE;
    return AnalysisCacheKey.unit(ruleSet, unit, view, related);
  }

  private List<FlowDigests.Related> related(final SourceUnit unit, final String ruleSet) {
    final List<SourceUnit> closure = snapshot.closureOf(unit.path());
    return summaries.of(unit, ruleSet, closure)
        .map(digest -> List.of(new FlowDigests.Related("taint-summary", digest)))
        .orElseGet(() -> digests.of(closure));
  }

  private String ruleSet(final AnalysisLanguage language) {
    return AnalysisCacheKey.ruleSet(
        engineVersion,
        rules.stream().filter(rule -> rule.covers(language)).toList()
    );
  }
}
