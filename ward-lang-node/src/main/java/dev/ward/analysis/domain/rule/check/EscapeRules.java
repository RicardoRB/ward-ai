package dev.ward.analysis.domain.rule.check;

import java.util.Objects;
import java.util.Set;

/**
 * The characters that a backslash may escape in each context without being useless, as ESLint
 * {@code no-useless-escape} reads them. Digits and the characters that are not certain are never
 * reported.
 */
final class EscapeRules {

  private static final String LINE_TERMINATORS = "\n\r  ";
  private static final String ESCAPE_CHARS = "bfnrtvxu0";
  private static final String REGEX_LETTERS = "bBdDfnrsStvwWcxukpP";
  private static final String REGEX_OUTSIDE = "^$.*+?()[]{}|/";
  private static final String REGEX_INSIDE = "]-";
  private static final String UNCERTAIN = "0123456789^[";
  private static final char BACKSLASH = '\\';

  private final Set<String> allowedRegexCharacters;

  /**
   * Creates the rules.
   *
   * @param allowedRegexCharacters the characters whose escape a regular expression may keep
   */
  EscapeRules(final Set<String> allowedRegexCharacters) {
    this.allowedRegexCharacters = Set.copyOf(Objects.requireNonNull(allowedRegexCharacters));
  }

  /** Reports whether a string literal escape of {@code c} is valid for the given quote. */
  boolean string(final char c, final char quote) {
    return c == quote
        || c == BACKSLASH
        || ESCAPE_CHARS.indexOf(c) >= 0
        || LINE_TERMINATORS.indexOf(c) >= 0
        || UNCERTAIN.indexOf(c) >= 0
        || allowedRegexCharacters.contains(String.valueOf(c));
  }

  /** Reports whether a template literal escape of {@code c} is valid. */
  boolean template(final char c, final boolean followedByBrace) {
    return c == '`'
        || c == BACKSLASH
        || ESCAPE_CHARS.indexOf(c) >= 0
        || LINE_TERMINATORS.indexOf(c) >= 0
        || UNCERTAIN.indexOf(c) >= 0
        || c == '$' && followedByBrace;
  }

  /** Reports whether a regular expression escape of {@code c} is valid in the given context. */
  boolean regex(final char c, final boolean inClass) {
    if (REGEX_LETTERS.indexOf(c) >= 0 || UNCERTAIN.indexOf(c) >= 0 || c == BACKSLASH) {
      return true;
    }
    if (allowedRegexCharacters.contains(String.valueOf(c))) {
      return true;
    }
    return inClass ? REGEX_INSIDE.indexOf(c) >= 0 : REGEX_OUTSIDE.indexOf(c) >= 0;
  }

  /**
   * Reports whether an escape of {@code next} is valid, in the regular expression or string
   * context.
   */
  boolean valid(final char next, final boolean regex, final boolean inClass, final char quote) {
    return regex ? regex(next, inClass) : string(next, quote);
  }

  /** The character class state after reading {@code c} of a regular expression body. */
  static boolean nextInClass(final boolean inClass, final char c) {
    return c == '[' || inClass && c != ']';
  }

  /**
   * The character class state after a step: an escape keeps it, a regular expression character
   * moves it.
   */
  static boolean classState(
      final boolean regex, final boolean escape, final boolean inClass, final char c) {
    return regex && !escape ? nextInClass(inClass, c) : inClass;
  }
}
