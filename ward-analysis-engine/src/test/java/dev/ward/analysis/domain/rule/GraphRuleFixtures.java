package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.graph.DependencyGraph;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import java.util.List;
import java.util.Set;

/** Builds graphs and definitions for the graph rule tests. */
final class GraphRuleFixtures {

  private GraphRuleFixtures() {}

  /** An import on {@code line} of {@code from} that resolves to {@code to}. */
  static DependencyEdge edge(final String from, final String to, final int line) {
    final Span span = new Span(line, 1, line, 2, line * 100, line * 100 + 1);
    return new DependencyEdge(from, to, new Reference(to, span));
  }

  static DependencyGraph graph(final DependencyEdge... edges) {
    return new DependencyGraph(List.of(edges));
  }

  static RuleDefinition definition(final String id, final String body) {
    return new RuleDefinition(
        id,
        Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.PYTHON),
        RuleCapability.SYMBOLS,
        AnalysisSeverity.ERROR,
        body);
  }
}
