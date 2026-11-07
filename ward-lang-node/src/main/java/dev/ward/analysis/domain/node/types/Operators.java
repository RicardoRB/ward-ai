package dev.ward.analysis.domain.node.types;

import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

/** The types of binary operators, for the operand types the resolver knows. */
final class Operators {
  private static final String NUMBER = Literals.NUMBER;
  private static final String STRING = Literals.STRING;
  private static final String BIGINT = "bigint";
  private static final BinaryOperator<TsType> ARITHMETIC = Operators::arithmetic;
  private static final BinaryOperator<TsType> COMPARISON =
      (left, right) -> TsType.named(Literals.BOOLEAN);
  private static final Map<String, BinaryOperator<TsType>> TABLE =
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

  /** Returns the type of {@code left operator right}, or {@link TsType#UNKNOWN}. */
  static TsType binary(final String operator, final TsType left, final TsType right) {
    return TABLE.getOrDefault(operator, (a, b) -> TsType.UNKNOWN).apply(left, right);
  }

  /** Returns the union of two types, or unknown when either is. */
  static TsType join(final TsType first, final TsType second) {
    if (!first.isKnown() || !second.isKnown()) {
      return TsType.UNKNOWN;
    }
    return first.print().equals(second.print()) ? first : new TsType.Union(List.of(first, second));
  }

  /** Removes {@code null} and {@code undefined} from a union. */
  static TsType nonNull(final TsType type) {
    return type instanceof TsType.Union union ? without(union, false) : type;
  }

  private static TsType without(final TsType.Union union, final boolean falsy) {
    final List<TsType> kept =
        union.parts().stream()
            .filter(
                part -> !MemberValues.isNullish(part) && !(falsy && "false".equals(part.print())))
            .toList();
    return kept.size() == 1 ? kept.get(0) : new TsType.Union(kept);
  }

  private static TsType plus(final TsType left, final TsType right) {
    final String l = Literals.widen(left).print();
    final String r = Literals.widen(right).print();
    if (STRING.equals(l) || STRING.equals(r)) {
      return TsType.named(STRING);
    }
    if (!left.isKnown() || !right.isKnown()) {
      return TsType.UNKNOWN;
    }
    if (NUMBER.equals(l) && NUMBER.equals(r)) {
      return TsType.named(NUMBER);
    }
    return BIGINT.equals(l) && BIGINT.equals(r) ? left : TsType.UNKNOWN;
  }

  private static TsType arithmetic(final TsType left, final TsType right) {
    return BIGINT.equals(left.print()) && BIGINT.equals(right.print())
        ? left
        : TsType.named(NUMBER);
  }

  private static TsType coalesce(final TsType left, final TsType right) {
    return left.isKnown() ? join(nonNull(left), right) : TsType.UNKNOWN;
  }

  private static TsType or(final TsType left, final TsType right) {
    if (!left.isKnown()) {
      return TsType.UNKNOWN;
    }
    return join(left instanceof TsType.Union union ? without(union, true) : left, right);
  }

  private static TsType and(final TsType left, final TsType right) {
    final boolean same = left.isKnown() && right.isKnown() && left.print().equals(right.print());
    return same ? left : TsType.UNKNOWN;
  }
}
