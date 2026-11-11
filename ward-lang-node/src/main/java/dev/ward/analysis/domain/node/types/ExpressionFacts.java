package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import java.util.List;
import java.util.Optional;

/** The facts of expressions: their types, call targets and constant values. */
final class ExpressionFacts {
  private static final String VARIABLE = "variableDeclaration";
  private static final int MIN_CALL_PARTS = 2;

  private final UnitModel model;
  private final ExprTyper typer;
  private final MemberFacts memberFacts = new MemberFacts();

  ExpressionFacts(final UnitModel model, final ExprTyper typer) {
    this.model = model;
    this.typer = typer;
  }

  TypeRef type(final SyntaxNode node) {
    if (Exprs.isExpression(node.kind())) {
      return TypeRefs.of(typer.typeOf(node));
    }
    if (VARIABLE.equals(node.kind())) {
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

  CallTarget call(final SyntaxNode node) {
    if (!Exprs.SINGLE.equals(node.kind())) {
      return null;
    }
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (parts.size() < MIN_CALL_PARTS) {
      return null;
    }
    final boolean constructs = "new".equals(Exprs.token(parts.get(0)));
    final Optional<CallTyper.Call> resolved =
        constructs
            ? typer.constructed(node)
            : typer.calls().isCall(parts) ? typer.calls().call(node) : Optional.empty();
    return resolved
        .map(call -> TypeRefs.target(ownerName(call), call.name(), call.sig(), call.isStatic()))
        .orElse(null);
  }

  private String ownerName(final CallTyper.Call call) {
    return call.owner().isEmpty() ? model.unit().path() : call.owner();
  }

  /** The methods and fields of the type of an expression, or null when the type is unknown. */
  TypeMembers members(final SyntaxNode node) {
    return Exprs.isExpression(node.kind()) ? memberFacts.of(typer.typeOf(node)) : null;
  }

  /** The shape of the declaration behind the type of an expression, such as an enum. */
  TypeShape shapeOfType(final SyntaxNode node) {
    if (!Exprs.isExpression(node.kind())) {
      return null;
    }
    final Sym sym = ShapeFacts.ofType(typer.typeOf(node));
    return sym == null ? null : ShapeFacts.shapeOf(sym);
  }

  ConstantValue constant(final SyntaxNode node) {
    return Exprs.isExpression(node.kind()) ? ConstantFacts.of(typer.typeOf(node)) : null;
  }
}
