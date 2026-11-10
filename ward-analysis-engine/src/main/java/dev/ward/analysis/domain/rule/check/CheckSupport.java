package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Optional;

/** Argument checks and message filling shared by the checks. */
final class CheckSupport {

  /** The placeholder for the measured value in a message. */
  static final String VALUE = "{value}";

  /** The placeholder for the limit in a message. */
  static final String MAX = "{max}";

  private CheckSupport() {}

  /**
   * Returns {@code value} after checking that it is not negative.
   *
   * @throws IllegalArgumentException if {@code value} is negative
   */
  static int nonNegative(final int value, final String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
    return value;
  }

  /** Fills the value and limit placeholders of a rule message. */
  static String fill(final String template, final String value, final String max) {
    return template.replace(VALUE, value).replace(MAX, max);
  }

  /**
   * Tells whether a declaration with these modifiers passes the modifier filter: it must have
   * {@code required} if one is given, and must not have {@code excluded}.
   */
  static boolean admits(
      final List<String> modifiers,
      final Optional<String> required,
      final Optional<String> excluded) {
    final boolean hasRequired = required.map(modifiers::contains).orElse(true);
    final boolean hasExcluded = excluded.map(modifiers::contains).orElse(false);
    return hasRequired && !hasExcluded;
  }

  /**
   * Returns the match at {@code span} with {@code message}; a loop calls this, not the constructor.
   */
  static RuleMatch at(final Span span, final String message) {
    return new RuleMatch(span, message);
  }
}
