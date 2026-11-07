package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Folds compile-time constants of Java expressions: literals, signs and string concatenation. */
final class Constants {
  private static final int SIGNED = 2;
  private static final int PARENTHESIZED = 3;
  private static final String PRIMARY = "primary";
  private static final String LITERAL = "literal";
  private static final String OPEN = "(";
  private static final String MINUS = "-";

  private Constants() {}

  /** Returns the value of {@code expression} when it is a constant made of literals. */
  static Optional<Object> literalValue(final SyntaxNode expression) {
    final List<SyntaxNode> parts = expression.children();
    return switch (parts.size()) {
      case 1 -> single(parts.get(0));
      case SIGNED ->
          MINUS.equals(Syntax.token(parts.get(0)))
              ? literalValue(parts.get(1)).flatMap(Constants::negate)
              : Optional.empty();
      case PARENTHESIZED -> parenthesized(parts);
      default -> Optional.empty();
    };
  }

  private static Optional<Object> parenthesized(final List<SyntaxNode> parts) {
    return OPEN.equals(Syntax.token(parts.get(0))) ? literalValue(parts.get(1)) : Optional.empty();
  }

  private static Optional<Object> single(final SyntaxNode child) {
    if (!PRIMARY.equals(child.kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> inner = child.children();
    return switch (inner.size()) {
      case 1 ->
          LITERAL.equals(inner.get(0).kind())
              ? Literals.of(inner.get(0)).flatMap(Literals.Literal::value)
              : Optional.empty();
      case PARENTHESIZED -> parenthesized(inner);
      default -> Optional.empty();
    };
  }

  /** Returns the negation of a numeric constant. */
  static Optional<Object> negate(final Object value) {
    if (value instanceof Long number) {
      return Optional.of(-number);
    }
    if (value instanceof Double number) {
      return Optional.of(-number);
    }
    return Optional.empty();
  }
}
