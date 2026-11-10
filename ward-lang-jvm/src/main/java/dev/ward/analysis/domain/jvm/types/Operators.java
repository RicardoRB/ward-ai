package dev.ward.analysis.domain.jvm.types;

import java.util.Optional;

/** Folds constants through Java's arithmetic and string concatenation. */
final class Operators {
  private Operators() {}

  /** Returns the concatenation of two constants. */
  static Optional<Object> concat(final Optional<Object> left, final Optional<Object> right) {
    if (left.isPresent() && right.isPresent()) {
      return Optional.of(String.valueOf(left.get()) + right.get());
    }
    return Optional.empty();
  }

  /** Returns {@code left operator right} for numeric constants of the promoted type. */
  static Optional<Object> arithmetic(
      final String operator,
      final Optional<Object> left,
      final Optional<Object> right,
      final JType promoted) {
    if (left.isEmpty() || right.isEmpty() || !(promoted instanceof JType.Primitive primitive)) {
      return Optional.empty();
    }
    if (left.get() instanceof Long a && right.get() instanceof Long b && !isFloating(primitive)) {
      return integral(operator, a, b).map(value -> narrow(value, primitive));
    }
    if (left.get() instanceof Number a && right.get() instanceof Number b) {
      return floating(operator, a.doubleValue(), b.doubleValue());
    }
    return Optional.empty();
  }

  private static boolean isFloating(final JType.Primitive primitive) {
    return "double".equals(primitive.name()) || "float".equals(primitive.name());
  }

  private static Object narrow(final Long value, final JType.Primitive primitive) {
    return "int".equals(primitive.name()) ? (Object) (long) value.intValue() : value;
  }

  private static Optional<Long> integral(final String operator, final long a, final long b) {
    return switch (operator) {
      case "+" -> Optional.of(a + b);
      case "-" -> Optional.of(a - b);
      case "*" -> Optional.of(a * b);
      case "/" -> b == 0 ? Optional.empty() : Optional.of(a / b);
      case "%" -> b == 0 ? Optional.empty() : Optional.of(a % b);
      default -> Optional.empty();
    };
  }

  private static Optional<Object> floating(final String operator, final double a, final double b) {
    return switch (operator) {
      case "+" -> Optional.of(a + b);
      case "-" -> Optional.of(a - b);
      case "*" -> Optional.of(a * b);
      case "/" -> Optional.of(a / b);
      default -> Optional.empty();
    };
  }
}
