package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import dev.ward.analysis.domain.ir.DependencyEdge;
import dev.ward.analysis.domain.ir.Reference;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** Builds layer rules and edges for the layer rule tests. */
final class LayerRuleFixtures {

  private LayerRuleFixtures() {}

  static List<RuleMatch> match(final List<LayerBoundary> boundaries, final DependencyEdge edge) {
    return rule(boundaries).matches(root(), view(edge));
  }

  static CompiledRule rule(final List<LayerBoundary> boundaries) {
    final RuleDefinition definition =
        new RuleDefinition(
            LayerDependencyRule.ID,
            Set.of(AnalysisLanguage.JAVA, AnalysisLanguage.PYTHON),
            RuleCapability.SYMBOLS,
            AnalysisSeverity.ERROR,
            LayerRuleBody.encode(boundaries));
    return new LayerDependencyRule(definition, boundaries);
  }

  static DependencyEdge edge(final String from, final String to, final int line, final int column) {
    final Span span = new Span(line, column, line, column + 1, 0, 1);
    return new DependencyEdge(from, to, new Reference("import", span));
  }

  static SyntaxNode root() {
    return null;
  }

  private static DependencyView view(final DependencyEdge edge) {
    return new DependencyView(List.of(), List.of(), List.of(edge));
  }
}
