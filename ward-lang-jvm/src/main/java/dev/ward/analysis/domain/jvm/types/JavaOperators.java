package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Types the operators of Java: arithmetic, comparison, casts, conditionals and indexing. */
final class JavaOperators {
  private static final String BOOLEAN = "boolean";
  private static final String INT = "int";
  private static final Set<String> COMPARISONS =
      Set.of("<", ">", "<=", ">=", "==", "!=", "&&", "||");
  private static final Set<String> BITWISE = Set.of("&", "|", "^");

  private final Session session;
  private final JavaExpressions expressions;
  private final JavaTyper typer;

  JavaOperators(
      final Session session, final JavaExpressions expressions, final JavaTyper typer) {
    this.session = session;
    this.expressions = expressions;
    this.typer = typer;
  }

  Typed index(final List<SyntaxNode> parts, final Ctx ctx) {
    final Typed array = expressions.expression(parts.get(0), ctx, Optional.empty());
    expressions.expression(parts.get(2), ctx, Optional.empty());
    return array.type()
        .filter(JType.ArrayType.class::isInstance)
        .map(found -> Typed.of(((JType.ArrayType) found).component()))
        .orElse(Typed.UNKNOWN);
  }

  Typed cast(final SyntaxNode node, final Ctx ctx) {
    final List<SyntaxNode> parts = node.children();
    final Optional<JType> type =
        Syntax.child(node, "typeType").flatMap(written -> JavaTypes.type(written, ctx.scope()));
    expressions.expression(parts.get(parts.size() - 1), ctx, type);
    return Typed.of(type);
  }

  Typed ternary(final List<SyntaxNode> parts, final Ctx ctx, final Optional<JType> target) {
    expressions.expression(parts.get(0), ctx, Optional.empty());
    final Typed yes = expressions.expression(parts.get(2), ctx, target);
    final Typed no = expressions.expression(parts.get(4), ctx, target);
    return Typed.of(unify(yes.type(), no.type()));
  }

  /** Returns the common type of two branches, empty when they have none that is known. */
  Optional<JType> unify(final Optional<JType> first, final Optional<JType> second) {
    if (first.isEmpty() || second.isEmpty()) {
      return Optional.empty();
    }
    final JType a = first.get();
    final JType b = second.get();
    if (a.equals(b)) {
      return first;
    }
    if (Types.isNull(a)) {
      return Optional.of(Types.boxed(b));
    }
    if (Types.isNull(b)) {
      return Optional.of(Types.boxed(a));
    }
    final JType numeric = Types.promote(a, b);
    if (numeric != null && (Types.isPrimitive(a) || Types.isPrimitive(b))) {
      return Optional.of(numeric);
    }
    return commonSuperclass(Types.boxed(a), Types.boxed(b));
  }

  private Optional<JType> commonSuperclass(final JType a, final JType b) {
    if (!(a instanceof JType.ClassType first)) {
      return Optional.empty();
    }
    Optional<JType.ClassType> current = Optional.of(first);
    while (current.isPresent()) {
      final JType.ClassType candidate = JType.ClassType.of(current.get().name());
      if (session.assignability.assignable(b, candidate, false)) {
        return Optional.of(candidate);
      }
      current = session.hierarchy.find(candidate.name()).flatMap(JvmClass::superclass);
    }
    return Optional.empty();
  }

  Typed unary(final String operator, final Typed operand) {
    if ("!".equals(operator)) {
      return Typed.of(new JType.Primitive(BOOLEAN));
    }
    if (operand.type().isEmpty()) {
      return Typed.UNKNOWN;
    }
    if ("++".equals(operator) || "--".equals(operator)) {
      return Typed.of(operand.type());
    }
    final JType promoted = Types.promote(operand.type().get(), new JType.Primitive(INT));
    final Typed typed = promoted == null ? Typed.UNKNOWN : Typed.of(promoted);
    return "-".equals(operator)
        ? typed.withConstant(operand.constant().flatMap(Constants::negate))
        : typed;
  }

  Typed binary(final List<SyntaxNode> parts, final Ctx ctx) {
    final Typed left = expressions.expression(parts.get(0), ctx, Optional.empty());
    final Typed right =
        expressions.expression(parts.get(parts.size() - 1), ctx, Optional.empty());
    final List<String> tokens = new ArrayList<>();
    parts.subList(1, parts.size() - 1).forEach(part -> tokens.add(Syntax.token(part)));
    final String operator = String.join("", tokens);
    if (COMPARISONS.contains(operator)) {
      return Typed.of(new JType.Primitive(BOOLEAN));
    }
    if (left.type().isEmpty() || right.type().isEmpty()) {
      return Typed.UNKNOWN;
    }
    return arithmetic(operator, left, right);
  }

  private static Typed arithmetic(final String operator, final Typed left, final Typed right) {
    final JType a = left.type().get();
    final JType b = right.type().get();
    if ("+".equals(operator) && (isString(a) || isString(b))) {
      return Typed.of(JType.ClassType.of(Types.STRING))
          .withConstant(Operators.concat(left.constant(), right.constant()));
    }
    if (operator.startsWith("<<") || operator.startsWith(">>")) {
      return Optional.ofNullable(Types.promote(a, new JType.Primitive(INT)))
          .map(Typed::of)
          .orElse(Typed.UNKNOWN);
    }
    if (BITWISE.contains(operator) && Types.unboxed(a).equals(new JType.Primitive(BOOLEAN))) {
      return Typed.of(new JType.Primitive(BOOLEAN));
    }
    final JType promoted = Types.promote(a, b);
    if (promoted == null) {
      return Typed.UNKNOWN;
    }
    return Typed.of(promoted)
        .withConstant(Operators.arithmetic(operator, left.constant(), right.constant(), promoted));
  }

  private static boolean isString(final JType type) {
    return type instanceof JType.ClassType named && Types.STRING.equals(named.name());
  }

  Typed switchExpression(final SyntaxNode node, final Ctx ctx) {
    final List<Optional<JType>> results = new ArrayList<>();
    typer.walkSwitch(node, ctx, results);
    if (results.isEmpty()) {
      return Typed.UNKNOWN;
    }
    Optional<JType> merged = results.get(0);
    for (final Optional<JType> next : results.subList(1, results.size())) {
      merged = unify(merged, next);
    }
    return Typed.of(merged);
  }
}
