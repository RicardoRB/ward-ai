package dev.ward.analysis.domain.node.types;

import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

/** The types of binary operators, for the operand types the resolver knows. */
final class Operators {
  private static final String NUMBER = Literals.NUMBER;
  private static final String STRING = Literals.STRING;
  private static final String BIGINT = "bigint";
  private static final BinaryOperator<Ty> ARITHMETIC = Operators::arithmetic;
  private static final BinaryOperator<Ty> COMPARISON = (left, right) -> Ty.named(Literals.BOOLEAN);
  private static final Map<String, BinaryOperator<Ty>> TABLE =
      Map.ofEntries(
          Map.entry("+", Operators::plus),
          Map.entry("-", ARITHMETIC),
          Map.entry("*", ARITHMETIC),
          Map.entry("/", ARITHMETIC),
          Map.entry("%", ARITHMETIC),
          Map.entry("**", ARITHMETIC),
          Map.entry("<<", ARITHMETIC),
          Map.entry("&", ARITHMETIC),
          Map.entry("|", ARITHMETIC),
          Map.entry("^", ARITHMETIC),
          Map.entry("==", COMPARISON),
          Map.entry("!=", COMPARISON),
          Map.entry("===", COMPARISON),
          Map.entry("!==", COMPARISON),
          Map.entry("<", COMPARISON),
          Map.entry(">", COMPARISON),
          Map.entry("<=", COMPARISON),
          Map.entry(">=", COMPARISON),
          Map.entry("instanceof", COMPARISON),
          Map.entry("in", COMPARISON),
          Map.entry("??", Operators::coalesce),
          Map.entry("||", Operators::or),
          Map.entry("&&", Operators::and));

  private Operators() {}

  /** Returns the type of {@code left operator right}, or {@link Ty#UNKNOWN}. */
  static Ty binary(final String operator, final Ty left, final Ty right) {
    return TABLE.getOrDefault(operator, (a, b) -> Ty.UNKNOWN).apply(left, right);
  }

  /** Returns the union of two types, or unknown when either is. */
  static Ty join(final Ty first, final Ty second) {
    if (!first.isKnown() || !second.isKnown()) {
      return Ty.UNKNOWN;
    }
    return first.print().equals(second.print()) ? first : new Ty.Union(List.of(first, second));
  }

  /** Removes {@code null} and {@code undefined} from a union. */
  static Ty nonNull(final Ty type) {
    return type instanceof Ty.Union union ? without(union, false) : type;
  }

  private static Ty without(final Ty.Union union, final boolean falsy) {
    final List<Ty> kept =
        union.parts().stream()
            .filter(part -> !Members.isNullish(part) && !(falsy && "false".equals(part.print())))
            .toList();
    return kept.size() == 1 ? kept.get(0) : new Ty.Union(kept);
  }

  private static Ty plus(final Ty left, final Ty right) {
    final String l = Literals.widen(left).print();
    final String r = Literals.widen(right).print();
    if (STRING.equals(l) || STRING.equals(r)) {
      return Ty.named(STRING);
    }
    if (!left.isKnown() || !right.isKnown()) {
      return Ty.UNKNOWN;
    }
    if (NUMBER.equals(l) && NUMBER.equals(r)) {
      return Ty.named(NUMBER);
    }
    return BIGINT.equals(l) && BIGINT.equals(r) ? left : Ty.UNKNOWN;
  }

  private static Ty arithmetic(final Ty left, final Ty right) {
    return BIGINT.equals(left.print()) && BIGINT.equals(right.print()) ? left : Ty.named(NUMBER);
  }

  private static Ty coalesce(final Ty left, final Ty right) {
    return left.isKnown() ? join(nonNull(left), right) : Ty.UNKNOWN;
  }

  private static Ty or(final Ty left, final Ty right) {
    if (!left.isKnown()) {
      return Ty.UNKNOWN;
    }
    return join(left instanceof Ty.Union union ? without(union, true) : left, right);
  }

  private static Ty and(final Ty left, final Ty right) {
    final boolean same = left.isKnown() && right.isKnown() && left.print().equals(right.print());
    return same ? left : Ty.UNKNOWN;
  }
}
