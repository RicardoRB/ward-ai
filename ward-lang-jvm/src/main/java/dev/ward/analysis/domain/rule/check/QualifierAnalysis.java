package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import dev.ward.analysis.domain.jvm.JavaControlFlow;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the JSR-305 type qualifiers a file declares, the way SpotBugs' {@code CheckTypeQualifiers}
 * does for one body at a time: what flows forward from the parameters, calls and fields that carry
 * a qualifier meets what the code later requires of it, and the two must not conflict. Only
 * qualifiers declared in the file, and methods and fields of the file, take part.
 */
final class QualifierAnalysis {

  private QualifierAnalysis() {}

  /** Analyses {@code root}, the syntax of {@code unit}. */
  static List<QualChecks.Found> of(
      final SyntaxNode root, final SourceUnit unit, final UnitTypeFacts types) {
    final QualifierScope scope = QualifierScope.of(root);
    final List<QualChecks.Found> found = new ArrayList<>();
    if (scope.qualifiers().isEmpty()) {
      return found;
    }
    for (final ControlFlowGraph body : new JavaControlFlow().lower(unit, root).graphs()) {
      for (final QualifierScope.Qualifier qualifier : scope.qualifiers().values()) {
        found.addAll(body(body, scope, qualifier, types));
      }
    }
    return found;
  }

  private static List<QualChecks.Found> body(
      final ControlFlowGraph body,
      final QualifierScope scope,
      final QualifierScope.Qualifier qualifier,
      final UnitTypeFacts types) {
    final String name = body.body();
    final int hash = name.indexOf('#');
    final String owner = hash < 0 ? "" : name.substring(0, hash);
    final String type =
        owner.substring(Math.max(owner.lastIndexOf('.'), owner.lastIndexOf('$')) + 1);
    final QualifierScope.Method method =
        hash < 0 ? null : scope.methods().get(type + name.substring(hash));
    final QualSites sites = new QualSites(types, scope, qualifier, type, method);
    final NullGraph graph = NullGraph.of(body);
    final QualForward forward = new QualForward(graph, sites);
    final QualBackward backward = new QualBackward(graph, sites);
    final List<VariableAccess> parameters = NullSolved.parameters(body);
    final Map<Span, QualState.Held> initial = initial(parameters, method, qualifier);
    final QualReport report = new QualReport(graph, sites, forward, backward);
    return report.run(parameters, initial);
  }

  private static Map<Span, QualState.Held> initial(
      final List<VariableAccess> parameters,
      final QualifierScope.Method method,
      final QualifierScope.Qualifier qualifier) {
    final Map<Span, QualState.Held> state = new HashMap<>();
    for (int index = 0; index < parameters.size(); index++) {
      final QualWhen declared =
          method == null || index >= method.parameters().size()
              ? null
              : method.parameters().get(index).get(qualifier.name());
      final QualState.Source source = QualSites.source(QualState.Origin.PARAMETER, declared);
      state.put(parameters.get(index).declaration(), held(source));
    }
    return state;
  }

  private static QualState.Held held(final QualState.Source source) {
    return new QualState.Held(QualFlow.of(source.when()), Set.of(source));
  }
}
