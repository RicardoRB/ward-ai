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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Answers the facts of the nodes of one unit. */
final class FactBuilder {
  private final UnitModel model;
  private final ExprTyper typer;
  private final DeclarationFacts declarations;

  FactBuilder(final UnitModel model, final ExprTyper typer) {
    this.model = model;
    this.typer = typer;
    this.declarations = new DeclarationFacts(model, typer);
  }

  /** Returns what is known about {@code span}, or null when nothing is. */
  SpanFacts at(final Span span, final Set<FactKind> kinds) {
    final List<SyntaxNode> nodes = model.nodesAt(span);
    final TypeRef type = asked(kinds, FactKind.TYPE, () -> first(nodes, this::type));
    final TypeShape shape = asked(kinds, FactKind.SHAPE, () -> first(nodes, declarations::shape));
    final CallTarget call = asked(kinds, FactKind.CALL, () -> first(nodes, this::call));
    final ConstantValue constant =
        asked(kinds, FactKind.CONSTANT, () -> first(nodes, this::constant));
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

  private TypeRef type(final SyntaxNode node) {
    if (Exprs.isExpression(node.kind())) {
      return TypeRefs.of(typer.typeOf(node));
    }
    if ("variableDeclaration".equals(node.kind())) {
      return Optional.ofNullable(siteOf(node))
          .map(typer.names()::variable)
          .map(TypeRefs::of)
          .orElse(null);
    }
    return null;
  }

  private UnitModel.Site siteOf(final SyntaxNode declaration) {
    final SyntaxNode identifier = TreeText.variableName(declaration);
    return identifier == null ? null : model.site(identifier.span());
  }

  private CallTarget call(final SyntaxNode node) {
    if (!Exprs.SINGLE.equals(node.kind())) {
      return null;
    }
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (parts.size() < 2) {
      return null;
    }
    final boolean constructs = "new".equals(Exprs.token(parts.get(0)));
    final Optional<CallTyper.Call> resolved =
        constructs
            ? typer.constructs().constructed(node)
            : typer.calls().isCall(parts) ? typer.calls().call(node) : Optional.empty();
    return resolved
        .map(call -> TypeRefs.target(ownerName(call), call.name(), call.sig(), call.isStatic()))
        .orElse(null);
  }

  private String ownerName(final CallTyper.Call call) {
    return call.owner().isEmpty() ? model.unit().path() : call.owner();
  }

  private ConstantValue constant(final SyntaxNode node) {
    return Exprs.isExpression(node.kind()) ? ConstantFacts.of(typer.typeOf(node)) : null;
  }
}
