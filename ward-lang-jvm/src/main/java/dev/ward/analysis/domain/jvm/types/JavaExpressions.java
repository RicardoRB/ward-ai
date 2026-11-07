package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Types the expressions of Java by their shape, and hands each form to the class that knows it. */
final class JavaExpressions {
  private static final String EXPRESSION = "expression";
  private static final String BOOLEAN = "boolean";
  private static final int SINGLE = 1;
  private static final int PAIR = 2;
  private static final int TERNARY = 5;
  private static final int INDEX = 4;
  private static final int TRIPLE = 3;
  private static final String NEW = "new";
  private static final String OPEN = "(";
  private static final String DOT = ".";
  private static final String INDEX_OPEN = "[";
  private static final String REFERENCE = "::";
  private static final String INSTANCE_OF = "instanceof";
  private static final String CONDITIONAL = "?";
  private static final Set<String> ASSIGNMENTS =
      Set.of("=", "+=", "-=", "*=", "/=", "&=", "|=", "^=", ">>=", ">>>=", "<<=", "%=");
  private static final Set<String> PREFIX = Set.of("+", "-", "++", "--", "~", "!");

  private final Session session;
  private final JavaTyper typer;
  private final JavaCalls calls;
  private final JavaNames names;
  private final JavaOperators operators;

  JavaExpressions(final Session session, final JavaTyper typer, final JavaCalls calls) {
    this.session = session;
    this.typer = typer;
    this.calls = calls;
    this.names = new JavaNames(session, this, calls, typer);
    this.operators = new JavaOperators(session, this, typer);
  }

  /** Types {@code node}, an {@code expression}, and everything inside it. */
  Typed expression(final SyntaxNode node, final Ctx ctx, final Optional<JType> target) {
    final Typed typed = dispatch(node, ctx, target);
    session.record(node, typed);
    return typed;
  }

  /** Returns the common type of two branches, empty when they have none that is known. */
  Optional<JType> unify(final Optional<JType> first, final Optional<JType> second) {
    return operators.unify(first, second);
  }

  private Typed dispatch(final SyntaxNode node, final Ctx ctx, final Optional<JType> target) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode first = parts.get(0);
    if (parts.size() == SINGLE) {
      return single(first, ctx, target);
    }
    if (EXPRESSION.equals(first.kind())) {
      return withLeft(parts, ctx, target);
    }
    final String lead = Syntax.token(first);
    if (NEW.equals(lead)) {
      return calls.creators().creator(parts.get(1), ctx, target);
    }
    if (OPEN.equals(lead)) {
      return operators.cast(node, ctx);
    }
    if (PREFIX.contains(lead) && parts.size() == PAIR) {
      return operators.unary(lead, expression(parts.get(1), ctx, Optional.empty()));
    }
    typer.walkChildren(node, ctx);
    return Typed.UNKNOWN;
  }

  private Typed single(final SyntaxNode child, final Ctx ctx, final Optional<JType> target) {
    return switch (child.kind()) {
      case "primary" -> primary(child, ctx);
      case "methodCall" -> calls.unqualified(child, ctx);
      case "lambdaExpression" -> calls.lambdas().lambda(child, ctx, target);
      case "switchExpression" -> operators.switchExpression(child, ctx);
      default -> {
        typer.walkChildren(child, ctx);
        yield Typed.UNKNOWN;
      }
    };
  }

  private Typed withLeft(
      final List<SyntaxNode> parts, final Ctx ctx, final Optional<JType> target) {
    final String operator = Syntax.token(parts.get(1));
    if (DOT.equals(operator)) {
      return names.member(parts, ctx);
    }
    if (INDEX_OPEN.equals(operator) && parts.size() == INDEX) {
      return operators.index(parts, ctx);
    }
    if (REFERENCE.equals(operator)) {
      expression(parts.get(0), ctx, Optional.empty());
      return Typed.of(target);
    }
    if (parts.size() == PAIR) {
      return expression(parts.get(0), ctx, Optional.empty());
    }
    return infix(parts, operator, ctx, target);
  }

  private Typed infix(
      final List<SyntaxNode> parts,
      final String operator,
      final Ctx ctx,
      final Optional<JType> target) {
    if (INSTANCE_OF.equals(operator)) {
      expression(parts.get(0), ctx, Optional.empty());
      names.instanceOf(parts.get(2), ctx);
      return Typed.of(new JType.Primitive(BOOLEAN));
    }
    if (CONDITIONAL.equals(operator) && parts.size() == TERNARY) {
      return operators.ternary(parts, ctx, target);
    }
    if (ASSIGNMENTS.contains(operator) && parts.size() == TRIPLE) {
      final Typed left = expression(parts.get(0), ctx, Optional.empty());
      expression(parts.get(2), ctx, left.type());
      return Typed.of(left.type());
    }
    return operators.binary(parts, ctx);
  }

  private Typed primary(final SyntaxNode node, final Ctx ctx) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode first = parts.get(0);
    if (parts.size() == SINGLE) {
      return simplePrimary(first, ctx);
    }
    final boolean three = parts.size() == TRIPLE;
    if (three && OPEN.equals(Syntax.token(first))) {
      return expression(parts.get(1), ctx, Optional.empty());
    }
    if (three && "class".equals(Syntax.token(parts.get(2)))) {
      return names.classLiteral(parts.get(0), ctx);
    }
    typer.walkChildren(node, ctx);
    return Typed.UNKNOWN;
  }

  private Typed simplePrimary(final SyntaxNode first, final Ctx ctx) {
    return switch (first.kind()) {
      case "literal" ->
          Literals.of(first)
              .map(literal -> Typed.of(literal.type()).withConstant(literal.value()))
              .orElse(Typed.UNKNOWN);
      case "identifier" -> names.name(first, ctx);
      default -> keyword(Syntax.token(first), ctx);
    };
  }

  private Typed keyword(final String word, final Ctx ctx) {
    return switch (word) {
      case "this" ->
          ctx.scope().enclosing().stream()
              .findFirst()
              .map(owner -> Typed.of(session.self(owner)))
              .orElse(Typed.UNKNOWN);
      case "super" -> names.superType(ctx);
      default -> Typed.UNKNOWN;
    };
  }
}
