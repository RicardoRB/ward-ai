package dev.ward.analysis.domain.jvm.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Folds compile-time constants of Java expressions: literals, signs and string concatenation. */
final class Constants {
  private static final int BINARY = 3;

  private Constants() {}

  /** Returns the value of {@code expression} when it is a constant made of literals. */
  static Optional<Object> literalValue(final SyntaxNode expression) {
    final List<SyntaxNode> parts = expression.children();
    if (parts.size() == 1) {
      return single(parts.get(0));
    }
    if (parts.size() == 2 && "-".equals(Syntax.token(parts.get(0)))) {
      return literalValue(parts.get(1)).flatMap(Constants::negate);
    }
    if (parts.size() == BINARY && "(".equals(Syntax.token(parts.get(0)))) {
      return literalValue(parts.get(1));
    }
    return Optional.empty();
  }

  private static Optional<Object> single(final SyntaxNode child) {
    if ("primary".equals(child.kind())) {
      final List<SyntaxNode> inner = child.children();
      if (inner.size() == 1 && "literal".equals(inner.get(0).kind())) {
        return Literals.of(inner.get(0)).flatMap(Literals.Literal::value);
      }
      if (inner.size() == BINARY && "(".equals(Syntax.token(inner.get(0)))) {
        return literalValue(inner.get(1));
      }
    }
    return Optional.empty();
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
