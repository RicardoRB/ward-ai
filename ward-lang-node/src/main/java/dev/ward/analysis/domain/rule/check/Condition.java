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
  private static final String ALT = "alt";
  private static final String MULTIPLE = "multiple";
  private static final String SIZE = "size";
  private static final int SINGLE = 1;

  private final String[] parts;

  private Condition(final String[] parts) {
    this.parts = parts.clone();
  }

  static Condition parse(final String text) {
    return new Condition(text.split("\\+"));
  }

  /**
   * Tells whether the condition holds: {@code has} tells whether an attribute is present, and
   * {@code literals} gives the static value of each attribute that has one.
   */
  boolean holds(final Predicate<String> has, final Map<String, String> literals) {
    return Arrays.stream(parts).allMatch(part -> part.equals(ALWAYS) || part(part, has, literals));
  }

  private static boolean part(
      final String part, final Predicate<String> has, final Map<String, String> literals) {
    if (part.startsWith("has:")) {
      return has.test(part.substring("has:".length()));
    }
    if (part.startsWith("has-any:")) {
      return Arrays.stream(part.substring("has-any:".length()).split("\\|")).anyMatch(has);
    }
    if (part.startsWith("eq:")) {
      return equalsLiteral(part.substring("eq:".length()), literals);
    }
    if (part.startsWith("eqany:")) {
      final String[] pair = part.substring("eqany:".length()).split("=", 2);
      final String value = literals.get(pair[0]);
      return value != null && Set.of(pair[1].split("\\|")).contains(value);
    }
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
