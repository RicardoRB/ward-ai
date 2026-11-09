package dev.ward.analysis.domain.rule.check;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A condition of an implicit-role row, written as in {@code aria-implicit-roles.tsv}: {@code -},
 * {@code has:A}, {@code eq:A=V}, {@code eqany:A=V1|V2}, {@code has-any:A|B}, {@code alt-blank},
 * {@code alt-nonblank}, {@code select-single}, joined by {@code +} when several must hold.
 */
final class Condition {

  private static final String ALWAYS = "-";
  private static final String JOIN = "\\+";
  private static final String OR = "\\|";
  private static final String HAS = "has:";
  private static final String HAS_ANY = "has-any:";
  private static final String EQ = "eq:";
  private static final String EQ_ANY = "eqany:";
  private static final String ALT = "alt";
  private static final String MULTIPLE = "multiple";
  private static final String SIZE = "size";
  private static final int SINGLE = 1;

  private final String[] parts;

  private Condition(final String... parts) {
    this.parts = parts.clone();
  }

  static Condition parse(final String text) {
    return new Condition(text.split(JOIN));
  }

  /**
   * Tells whether the condition holds: {@code has} tells whether an attribute is present, and
   * {@code literals} gives the static value of each attribute that has one.
   */
  boolean holds(final Predicate<String> has, final Map<String, String> literals) {
    return Arrays.stream(parts).allMatch(part -> ALWAYS.equals(part) || part(part, has, literals));
  }

  private static boolean part(
      final String part, final Predicate<String> has, final Map<String, String> literals) {
    if (part.startsWith(HAS)) {
      return has.test(part.substring(HAS.length()));
    }
    if (part.startsWith(HAS_ANY)) {
      return Arrays.stream(part.substring(HAS_ANY.length()).split(OR)).anyMatch(has);
    }
    if (part.startsWith(EQ)) {
      return equalsLiteral(part.substring(EQ.length()), literals);
    }
    if (part.startsWith(EQ_ANY)) {
      return equalsAny(part.substring(EQ_ANY.length()), literals);
    }
    return keyword(part, has, literals);
  }

  private static boolean keyword(
      final String part, final Predicate<String> has, final Map<String, String> literals) {
    return switch (part) {
      case "alt-blank" -> literals.containsKey(ALT) && literals.get(ALT).isBlank();
      case "alt-nonblank" -> literals.containsKey(ALT) && !literals.get(ALT).isBlank();
      case "select-single" -> selectSingle(has, literals);
      default -> throw new IllegalArgumentException("unknown implicit-role condition: " + part);
    };
  }

  private static boolean equalsLiteral(final String pair, final Map<String, String> literals) {
    final String[] parts = pair.split("=", 2);
    return parts[1].equals(literals.get(parts[0]));
  }

  private static boolean equalsAny(final String pair, final Map<String, String> literals) {
    final String[] parts = pair.split("=", 2);
    final String value = literals.get(parts[0]);
    return value != null && Set.of(parts[1].split(OR)).contains(value);
  }

  private static boolean selectSingle(
      final Predicate<String> has, final Map<String, String> literals) {
    if (has.test(MULTIPLE)) {
      return false;
    }
    final String size = literals.get(SIZE);
    if (size == null) {
      return true;
    }
    try {
      return Integer.parseInt(size) <= SINGLE;
    } catch (final NumberFormatException exception) {
      return false;
    }
  }
}
