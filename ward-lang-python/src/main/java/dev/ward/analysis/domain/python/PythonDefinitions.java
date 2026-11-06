package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.scope.BindingKind;
import dev.ward.analysis.domain.scope.ScopeBuilder;
import dev.ward.analysis.domain.scope.ScopeKind;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles the Python nodes that open a scope: functions, classes, lambdas and comprehensions. The
 * defaults, annotations and bases of a definition are evaluated in the enclosing scope, and so is
 * the first iterable of a comprehension.
 */
final class PythonDefinitions {

  /** The kind of a comprehension clause. */
  static final String COMP_FOR = "comp_for";

  private static final String IN = "in";
  private static final String FOR = "for";
  private static final String ARROW = "->";
  private static final String COMP_IF = "comp_if";
  private static final String COMP_ITER = "comp_iter";
  private static final int PARAMETERS = 2;
  private static final int NAME = 1;

  private final ScopeBuilder builder;
  private final PythonBindings bindings;
  private final Consumer<SyntaxNode> visit;

  PythonDefinitions(
      final ScopeBuilder builder, final PythonBindings bindings, final Consumer<SyntaxNode> visit) {
    this.builder = builder;
    this.bindings = bindings;
    this.visit = visit;
  }

  void function(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final List<SyntaxNode> parameters =
        bindings.parameters(
            PythonSyntax.children(parts.get(PARAMETERS), "typedargslist").stream().findFirst());
    final int arrow = PythonNames.indexOf(parts, ARROW);
    if (arrow >= 0) {
      visit.accept(parts.get(arrow + 1));
    }
    bindings.define(parts.get(NAME), BindingKind.FUNCTION);
    builder.open(ScopeKind.FUNCTION, node.span());
    bindings.declareParameters(parameters);
    visit.accept(parts.get(parts.size() - 1));
    builder.close();
  }

  void classDefinition(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    PythonSyntax.children(node, "arglist").forEach(visit);
    bindings.define(parts.get(NAME), BindingKind.CLASS);
    builder.open(ScopeKind.CLASS, node.span());
    visit.accept(parts.get(parts.size() - 1));
    builder.close();
  }

  void lambda(final SyntaxNode node) {
    final List<SyntaxNode> parameters =
        bindings.parameters(PythonSyntax.children(node, "varargslist").stream().findFirst());
    builder.open(ScopeKind.LAMBDA, node.span());
    bindings.declareParameters(parameters);
    visit.accept(node.children().get(node.children().size() - 1));
    builder.close();
  }

  void comprehension(final SyntaxNode node) {
    final SyntaxNode first = PythonSyntax.children(node, COMP_FOR).get(0);
    visit.accept(first.children().get(PythonNames.indexOf(first.children(), IN) + 1));
    builder.open(ScopeKind.COMPREHENSION, node.span());
    clause(first, true);
    node.children().stream().filter(child -> !child.equals(first)).forEach(visit);
    builder.close();
  }

  private void clause(final SyntaxNode clause, final boolean first) {
    final List<SyntaxNode> parts = clause.children();
    if (COMP_FOR.equals(clause.kind())) {
      bindings.target(parts.get(PythonNames.indexOf(parts, FOR) + 1), true);
      if (!first) {
        visit.accept(parts.get(PythonNames.indexOf(parts, IN) + 1));
      }
    } else if (COMP_IF.equals(clause.kind())) {
      visit.accept(parts.get(1));
    }
    parts.stream()
        .filter(part -> COMP_ITER.equals(part.kind()))
        .forEach(next -> clause(next.children().get(0), false));
  }
}
