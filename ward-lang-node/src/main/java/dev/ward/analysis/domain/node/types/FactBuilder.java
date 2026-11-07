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
    final TypeRef type =
        FactPick.asked(kinds, FactKind.TYPE, () -> FactPick.first(nodes, expressions::type));
    final TypeShape shape =
        FactPick.asked(kinds, FactKind.SHAPE, () -> FactPick.first(nodes, declarations::shape));
    final CallTarget call =
        FactPick.asked(kinds, FactKind.CALL, () -> FactPick.first(nodes, expressions::call));
    final ConstantValue constant =
        FactPick.asked(
            kinds, FactKind.CONSTANT, () -> FactPick.first(nodes, expressions::constant));
    final List<String> annotations =
        FactPick.list(
            kinds, FactKind.ANNOTATIONS, () -> FactPick.firstList(nodes, declarations::decorators));
    final List<String> scopes =
        annotations.isEmpty() ? List.of() : declarations.enclosing(nodes.get(nodes.size() - 1));
    final List<CallTarget> overrides =
        FactPick.list(
            kinds, FactKind.OVERRIDES, () -> FactPick.firstList(nodes, declarations::overrides));
    final boolean none =
        type == null && shape == null && call == null && constant == null && overrides.isEmpty();
    return none && annotations.isEmpty()
        ? null
        : new SpanFacts(type, shape, call, annotations, scopes, overrides, List.of(), constant);
  }
}
