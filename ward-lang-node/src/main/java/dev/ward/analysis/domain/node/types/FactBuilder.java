package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Answers the facts of the nodes of one unit. */
final class FactBuilder {
  private final UnitModel model;
  private final DeclarationFacts declarations;
  private final ExpressionFacts expressions;

  FactBuilder(final UnitModel model, final ExprTyper typer) {
    this.model = model;
    this.declarations = new DeclarationFacts(model, typer);
    this.expressions = new ExpressionFacts(model, typer);
  }

  /** Returns what is known about {@code span}, or null when nothing is. */
  SpanFacts at(final Span span, final Set<FactKind> kinds) {
    final List<SyntaxNode> nodes = model.nodesAt(span);
    final TypeRef type = asked(kinds, FactKind.TYPE, () -> first(nodes, expressions::type));
    final TypeShape shape = asked(kinds, FactKind.SHAPE, () -> first(nodes, declarations::shape));
    final CallTarget call = asked(kinds, FactKind.CALL, () -> first(nodes, expressions::call));
    final ConstantValue constant =
        asked(kinds, FactKind.CONSTANT, () -> first(nodes, expressions::constant));
    final List<String> annotations =
        list(kinds, FactKind.ANNOTATIONS, () -> firstList(nodes, declarations::decorators));
    final List<String> scopes =
        annotations.isEmpty() ? List.of() : declarations.enclosing(nodes.get(nodes.size() - 1));
    final List<CallTarget> overrides =
        list(kinds, FactKind.OVERRIDES, () -> firstList(nodes, declarations::overrides));
    final boolean none =
        type == null && shape == null && call == null && constant == null && overrides.isEmpty();
    return none && annotations.isEmpty()
        ? null
        : new SpanFacts(type, shape, call, annotations, scopes, overrides, List.of(), constant);
  }

  private static <T> T asked(
      final Set<FactKind> kinds, final FactKind kind, final Supplier<T> fact) {
    return kinds.contains(kind) ? fact.get() : null;
  }

  private static <T> List<T> list(
      final Set<FactKind> kinds, final FactKind kind, final Supplier<List<T>> facts) {
    return kinds.contains(kind) ? facts.get() : List.of();
  }

  private static <T> T first(final List<SyntaxNode> nodes, final Function<SyntaxNode, T> fact) {
    for (final SyntaxNode node : nodes) {
      final T found = fact.apply(node);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static <T> List<T> firstList(
      final List<SyntaxNode> nodes, final Function<SyntaxNode, List<T>> facts) {
    for (final SyntaxNode node : nodes) {
      final List<T> found = facts.apply(node);
      if (!found.isEmpty()) {
        return found;
      }
    }
    return List.of();
  }
}
