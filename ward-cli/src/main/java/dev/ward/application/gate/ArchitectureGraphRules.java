package dev.ward.application.gate;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.graph.GraphMatch;
import dev.ward.analysis.domain.rule.graph.GraphRuleBody;
import dev.ward.analysis.domain.rule.graph.GraphRules;
import dev.ward.analysis.domain.rule.graph.IndependenceRule;
import dev.ward.analysis.domain.rule.graph.SliceCycleRule;
import dev.ward.domain.init.ArchitectureLayers;
import java.util.List;

/**
 * Builds the architecture graph rules of a check from its configured roots and modules, and turns
 * their matches into findings. The rules read the whole dependency graph of a run, so they run once
 * per run and their findings are never cached per unit.
 */
final class ArchitectureGraphRules {

  private ArchitectureGraphRules() {}

  /** Returns the slice cycle rule over {@code roots}. */
  static RuleDefinition cycles(final List<String> roots) {
    return definition(SliceCycleRule.ID, GraphRuleBody.encodeRoots(roots));
  }

  /** Returns the independence rule over {@code tables}, one module list per table. */
  static RuleDefinition independence(final List<List<String>> tables) {
    return definition(IndependenceRule.ID, GraphRuleBody.encodeTables(tables));
  }

  /** The findings of every graph rule among {@code rules}, over {@code graph}. */
  static List<AnalysisFinding> findings(
      final List<RuleDefinition> rules, final DependencyGraph graph) {
    return rules.stream()
        .filter(GraphRules::isGraphRule)
        .flatMap(
            rule ->
                GraphRules.compile(rule).matches(graph).stream().map(match -> finding(rule, match)))
        .toList();
  }

  private static AnalysisFinding finding(final RuleDefinition rule, final GraphMatch match) {
    final Span span = match.match().span();
    return new AnalysisFinding(
        rule.id(),
        rule.severity(),
        match.match().message(),
        match.file(),
        span == null ? null : span.startLine(),
        span == null ? null : span.startColumn(),
        null,
        match.match().path());
  }

  private static RuleDefinition definition(final String id, final String body) {
    return new RuleDefinition(
        id,
        ArchitectureLayers.ENGINE_LANGUAGES,
        RuleCapability.SYMBOLS,
        AnalysisSeverity.ERROR,
        body);
  }
}
