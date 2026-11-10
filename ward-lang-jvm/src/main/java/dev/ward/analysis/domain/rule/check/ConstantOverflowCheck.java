package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reports the outermost constant integer expression whose value does not fit its type. A constant
 * expression is made of integer literals, {@code Integer.MAX_VALUE}, {@code Integer.MIN_VALUE},
 * {@code Long.MAX_VALUE}, {@code Long.MIN_VALUE}, parentheses, signs and the operators {@code +},
 * {@code -} and {@code *}. It is a long when one of its operands is, and an int otherwise.
 */
public final class ConstantOverflowCheck implements RuleCheck {

  private static final String EXPRESSION = "expression";
  private static final String PRIMARY = "primary";
  private static final String LITERAL = "literal";
  private static final String MINUS = "-";
  private static final int INT_BITS = 32;
  private static final int LONG_BITS = 64;
  private static final int BINARY = 3;
  private static final int SIGNED = 2;
  private static final Map<String, Value> NAMED =
      Map.of(
          "Integer.MAX_VALUE",
              Value.of(BigInteger.ONE.shiftLeft(INT_BITS - 1).subtract(BigInteger.ONE), false),
          "Integer.MIN_VALUE", Value.of(BigInteger.ONE.shiftLeft(INT_BITS - 1).negate(), false),
          "Long.MAX_VALUE",
              Value.of(BigInteger.ONE.shiftLeft(LONG_BITS - 1).subtract(BigInteger.ONE), true),
          "Long.MIN_VALUE", Value.of(BigInteger.ONE.shiftLeft(LONG_BITS - 1).negate(), true));

  private final String message;

  /** A constant: its exact value, whether it is a long, and whether a step overflowed. */
  private record Value(BigInteger number, boolean isLong, boolean overflow) {

    static Value of(final BigInteger number, final boolean isLong) {
      return new Value(number, isLong, false);
    }

    boolean fits() {
      final int bits = isLong ? LONG_BITS : INT_BITS;
      return number.bitLength() < bits;
    }

    Value combine(final Value other, final BigInteger result) {
      final boolean wide = isLong || other.isLong;
      final Value exact = new Value(result, wide, overflow || other.overflow);
      return exact.fits() ? exact : new Value(result, wide, true);
    }
  }

  /**
   * Creates the check.
   *
   * @param message the message reported at the expression
   */
  public ConstantOverflowCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    collect(root, matches);
    return List.copyOf(matches);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> matches) {
    final Optional<Value> value =
        EXPRESSION.equals(node.kind()) ? evaluate(node) : Optional.empty();
    if (value.isPresent()) {
      if (value.get().overflow()) {
        matches.add(CheckSupport.at(node.span(), message));
      }
      return;
    }
    node.children().forEach(child -> collect(child, matches));
  }

  private static Optional<Value> evaluate(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final Optional<Value> named = Optional.ofNullable(NAMED.get(SyntaxWalk.text(node)));
    if (named.isPresent()) {
      return named;
    }
    return switch (parts.size()) {
      case 1 -> primary(parts.get(0));
      case SIGNED -> signed(parts);
      case BINARY -> binary(parts);
      default -> Optional.empty();
    };
  }

  private static Optional<Value> primary(final SyntaxNode node) {
    if (!PRIMARY.equals(node.kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> inner = node.children();
    if (inner.size() == 1 && LITERAL.equals(inner.get(0).kind())) {
      return literal(SyntaxWalk.text(inner.get(0)));
    }
    return inner.size() == BINARY && EXPRESSION.equals(inner.get(1).kind())
        ? evaluate(inner.get(1))
        : Optional.empty();
  }

  private static Optional<Value> signed(final List<SyntaxNode> parts) {
    return MINUS.equals(SyntaxWalk.text(parts.get(0))) && EXPRESSION.equals(parts.get(1).kind())
        ? evaluate(parts.get(1))
            .map(value -> value.combine(Value.of(BigInteger.ZERO, false), value.number().negate()))
        : Optional.empty();
  }

  private static Optional<Value> binary(final List<SyntaxNode> parts) {
    if (!EXPRESSION.equals(parts.get(0).kind()) || !EXPRESSION.equals(parts.get(2).kind())) {
      return Optional.empty();
    }
    final Optional<Value> left = evaluate(parts.get(0));
    final Optional<Value> right = evaluate(parts.get(2));
    if (left.isEmpty() || right.isEmpty()) {
      return Optional.empty();
    }
    final BigInteger a = left.get().number();
    final BigInteger b = right.get().number();
    return switch (SyntaxWalk.text(parts.get(1))) {
      case "+" -> Optional.of(left.get().combine(right.get(), a.add(b)));
      case MINUS -> Optional.of(left.get().combine(right.get(), a.subtract(b)));
      case "*" -> Optional.of(left.get().combine(right.get(), a.multiply(b)));
      default -> Optional.empty();
    };
  }

  private static Optional<Value> literal(final String text) {
    final String digits = text.replace("_", "");
    final boolean isLong = digits.endsWith("L") || digits.endsWith("l");
    final String number = isLong ? digits.substring(0, digits.length() - 1) : digits;
    final boolean decimal = !number.isEmpty() && number.chars().allMatch(Character::isDigit);
    return decimal && !(number.length() > 1 && number.startsWith("0"))
        ? Optional.of(Value.of(new BigInteger(number), isLong))
        : Optional.empty();
  }
}
