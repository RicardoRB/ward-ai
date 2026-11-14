package dev.ward.application.gate;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.graph.ProjectFiles;
import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.GraphMatch;
import dev.ward.analysis.domain.rule.GraphRuleBody;
import dev.ward.analysis.domain.rule.GraphRules;
import dev.ward.analysis.domain.rule.IndependenceRule;
import dev.ward.analysis.domain.rule.RuleCapability;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.SliceCycleRule;
import dev.ward.analysis.domain.rule.UnusedFileRule;
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

  /** Returns the unused-file rule, with {@code entries} as entry globs. */
  static RuleDefinition unusedFiles(final List<String> entries) {
    return definition(UnusedFileRule.ID, GraphRuleBody.encodeRoots(entries));
  }

  /** Returns the independence rule over {@code tables}, one module list per table. */
  static RuleDefinition independence(final List<List<String>> tables) {
    return definition(IndependenceRule.ID, GraphRuleBody.encodeTables(tables));
  }

  /** The findings of every graph rule among {@code rules}, over {@code graph}. */
  static List<AnalysisFinding> findings(
      final List<RuleDefinition> rules, final DependencyGraph graph) {
    return findings(rules, graph, ProjectFiles.EMPTY);
  }

  /**
   * The findings of every graph rule among {@code rules}, over {@code graph} and {@code project}.
   */
  static List<AnalysisFinding> findings(
      final List<RuleDefinition> rules, final DependencyGraph graph, final ProjectFiles project) {
    return rules.stream()
        .filter(GraphRules::isGraphRule)
        .flatMap(
            rule ->
                GraphRules.compile(rule).matches(graph, project).stream()
                    .map(match -> finding(rule, match)))
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

  static RuleDefinition definition(final String id, final String body) {
    return new RuleDefinition(
        id,
        ArchitectureLayers.ENGINE_LANGUAGES,
        RuleCapability.SYMBOLS,
        AnalysisSeverity.ERROR,
        body);
  }
}
