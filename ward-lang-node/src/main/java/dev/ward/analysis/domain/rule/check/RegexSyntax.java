package dev.ward.analysis.domain.rule.check;

import java.util.Optional;
import java.util.Set;

/**
 * A conservative ECMAScript regular expression syntax check, for the errors that a pattern written
 * by hand commonly has. It accepts what it cannot judge: lookbehinds, group names, property escapes
 * and the unicode-mode rules for escapes are not checked.
 */
final class RegexSyntax {

  private static final String VALID_FLAGS = "dgimsuyv";
  private static final int NONE = -1;
  private static final int LOOKBEHIND_PREFIX = 3;
  private static final int LOOKAHEAD_PREFIX = 2;
  private static final char BAR = '|';
  private static final char OPEN_GROUP = '(';
  private static final char CLOSE_GROUP = ')';
  private static final char OPEN_CLASS = '[';
  private static final char CLOSE_CLASS = ']';
  private static final char BACKSLASH = '\\';
  private static final char QUESTION_MARK = '?';
  private static final char OPEN_BRACE = '{';
  private static final char CLOSE_BRACE = '}';
  private static final char COMMA = ',';
  private static final char DASH = '-';
  private static final char CARET = '^';
  private static final char STAR = '*';
  private static final char PLUS = '+';
  private static final char BACKSPACE = '\b';
  private static final String TRAILING_BACKSLASH = "\\ at end of pattern";
  private static final String UNMATCHED = "Unmatched ')'";
  private static final String UNTERMINATED_GROUP = "Unterminated group";
  private static final String UNTERMINATED_CLASS = "Unterminated character class";
  private static final String NOTHING_TO_REPEAT = "Nothing to repeat";
  private static final String OUT_OF_ORDER_RANGE = "Range out of order in character class";
  private static final String OUT_OF_ORDER_QUANTIFIER = "numbers out of order in {} quantifier";

  private final String pattern;
  private int index;

  private RegexSyntax(final String pattern) {
    this.pattern = pattern;
  }

  /** The reason the pattern is invalid, or empty when this check accepts it. */
  static Optional<String> problem(final String pattern) {
    final RegexSyntax syntax = new RegexSyntax(pattern);
    final Optional<String> reason = syntax.disjunction(0);
    if (reason.isPresent()) {
      return reason;
    }
    return syntax.index < pattern.length() ? Optional.of(UNMATCHED) : Optional.empty();
  }

  /** The reason the flags are invalid, given the flags a caller allows beyond the valid ones. */
  static Optional<String> flagProblem(final String flags, final Set<String> allowed) {
    final StringBuilder seen = new StringBuilder();
    for (int position = 0; position < flags.length(); position++) {
      final char flag = flags.charAt(position);
      if (VALID_FLAGS.indexOf(flag) < 0 && !allowed.contains(String.valueOf(flag))) {
        return Optional.of("Invalid flags supplied to RegExp constructor '" + flags + "'");
      }
      if (seen.indexOf(String.valueOf(flag)) >= 0) {
        return Optional.of("Duplicate flags ('" + flag + "') supplied to RegExp constructor");
      }
      seen.append(flag);
    }
    if (flags.indexOf('u') >= 0 && flags.indexOf('v') >= 0) {
      return Optional.of("Regex 'u' and 'v' flags cannot be used together");
    }
    return Optional.empty();
  }

  /** Alternatives separated by a bar, up to the closing parenthesis of the enclosing group. */
  private Optional<String> disjunction(final int depth) {
    Optional<String> reason = alternative(depth);
    while (reason.isEmpty() && index < pattern.length() && pattern.charAt(index) == BAR) {
      index++;
      reason = alternative(depth);
    }
    return reason;
  }

  /** Terms up to a bar, a closing parenthesis or the end. */
  private Optional<String> alternative(final int depth) {
    while (index < pattern.length()) {
      final char c = pattern.charAt(index);
      if (c == BAR || c == CLOSE_GROUP) {
        return Optional.empty();
      }
      final Optional<String> reason = quantifiable(depth);
      if (reason.isPresent()) {
        return reason;
      }
    }
    return Optional.empty();
  }

  /** An atom and its quantifier, or a quantifier that has nothing to repeat. */
  private Optional<String> quantifiable(final int depth) {
    final char c = pattern.charAt(index);
    if (isQuantifierChar(c)) {
      return Optional.of(NOTHING_TO_REPEAT);
    }
    final Optional<String> atom = atom(depth);
    return atom.isPresent() ? atom : quantifier();
  }

  private Optional<String> atom(final int depth) {
    final char c = pattern.charAt(index);
    if (c == OPEN_GROUP) {
      return group(depth);
    }
    if (c == OPEN_CLASS) {
      return new Classes().characterClass();
    }
    if (c == BACKSLASH) {
      if (index + 1 >= pattern.length()) {
        return Optional.of(TRAILING_BACKSLASH);
      }
      index += 2;
      return Optional.empty();
    }
    if (c == OPEN_BRACE && Quantifiers.end(pattern, index) != NONE) {
      return Optional.of(NOTHING_TO_REPEAT);
    }
    index++;
    return Optional.empty();
  }

  private Optional<String> group(final int depth) {
    index++;
    if (index < pattern.length() && pattern.charAt(index) == QUESTION_MARK) {
      final Optional<String> prefix = groupPrefix();
      if (prefix.isPresent()) {
        return prefix;
      }
    }
    final Optional<String> inner = disjunction(depth + 1);
    if (inner.isPresent()) {
      return inner;
    }
    if (index >= pattern.length()) {
      return Optional.of(UNTERMINATED_GROUP);
    }
    index++;
    return Optional.empty();
  }

  /** The prefix of a group that starts with a question mark; empty when it is well formed. */
  private Optional<String> groupPrefix() {
    if (pattern.startsWith("?:", index)
        || pattern.startsWith("?=", index)
        || pattern.startsWith("?!", index)) {
      index += LOOKAHEAD_PREFIX;
    } else if (pattern.startsWith("?<=", index) || pattern.startsWith("?<!", index)) {
      index += LOOKBEHIND_PREFIX;
    } else if (pattern.startsWith("?<", index)) {
      final int close = pattern.indexOf('>', index);
      if (close < 0) {
        return Optional.of("Invalid capture group name");
      }
      index = close + 1;
    } else {
      return Optional.of("Invalid group");
    }
    return Optional.empty();
  }

  /** A quantifier after an atom, with its laziness mark. */
  private Optional<String> quantifier() {
    if (index >= pattern.length()) {
      return Optional.empty();
    }
    final char c = pattern.charAt(index);
    final boolean brace = c == OPEN_BRACE && Quantifiers.end(pattern, index) != NONE;
    if (!brace && !isQuantifierChar(c)) {
      return Optional.empty();
    }
    final Optional<String> bounds = brace ? braceQuantifier() : Optional.empty();
    if (bounds.isPresent()) {
      return bounds;
    }
    if (!brace) {
      index++;
    }
    if (index < pattern.length() && pattern.charAt(index) == QUESTION_MARK) {
      index++;
    }
    return Optional.empty();
  }

  private static boolean isQuantifierChar(final char c) {
    return c == STAR || c == PLUS || c == QUESTION_MARK;
  }

  /** A brace quantifier at the cursor, whose bounds must be in order. */
  private Optional<String> braceQuantifier() {
    final int end = Quantifiers.end(pattern, index);
    if (Quantifiers.outOfOrder(pattern.substring(index + 1, end - 1))) {
      return Optional.of(OUT_OF_ORDER_QUANTIFIER);
    }
    index = end;
    return Optional.empty();
  }

  /** The character class at the cursor: its characters, its ranges and its closing bracket. */
  private final class Classes {

    private Optional<String> characterClass() {
      index++;
      if (index < pattern.length() && pattern.charAt(index) == CARET) {
        index++;
      }
      while (index < pattern.length() && pattern.charAt(index) != CLOSE_CLASS) {
        final int low = classAtom();
        if (low == NONE) {
          return Optional.of(TRAILING_BACKSLASH);
        }
        final Optional<String> range = classRange(low);
        if (range.isPresent()) {
          return range;
        }
      }
      if (index >= pattern.length()) {
        return Optional.of(UNTERMINATED_CLASS);
      }
      index++;
      return Optional.empty();
    }

    /** A range that starts at the low character: its high end must not be lower. */
    private Optional<String> classRange(final int low) {
      if (index + 1 >= pattern.length()
          || pattern.charAt(index) != DASH
          || pattern.charAt(index + 1) == CLOSE_CLASS) {
        return Optional.empty();
      }
      index++;
      final int high = classAtom();
      if (high == NONE) {
        return Optional.of(TRAILING_BACKSLASH);
      }
      return low > high ? Optional.of(OUT_OF_ORDER_RANGE) : Optional.empty();
    }

    /**
     * One character or escape inside a class, as its code point, or NONE at a trailing backslash.
     */
    private int classAtom() {
      final char c = pattern.charAt(index);
      if (c != BACKSLASH) {
        index++;
        return c;
      }
      if (index + 1 >= pattern.length()) {
        return NONE;
      }
      final char escaped = pattern.charAt(index + 1);
      index += 2;
      return escaped == 'b' ? BACKSPACE : escaped;
    }
  }

  /** The brace quantifiers, read from a pattern without a cursor. */
  private static final class Quantifiers {

    /** The index after a brace quantifier at the start, or NONE when there is none. */
    static int end(final String pattern, final int start) {
      int at = digitsEnd(pattern, start + 1);
      if (at == start + 1 || at >= pattern.length()) {
        return NONE;
      }
      if (pattern.charAt(at) == COMMA) {
        at = digitsEnd(pattern, at + 1);
      }
      return at < pattern.length() && pattern.charAt(at) == CLOSE_BRACE ? at + 1 : NONE;
    }

    private static int digitsEnd(final String pattern, final int from) {
      int at = from;
      while (at < pattern.length() && Character.isDigit(pattern.charAt(at))) {
        at++;
      }
      return at;
    }

    /** Whether a brace quantifier's lower bound is above its upper bound. */
    static boolean outOfOrder(final String body) {
      final String[] bounds = body.split(String.valueOf(COMMA), -1);
      return bounds.length == 2
          && !bounds[1].isEmpty()
          && Long.parseLong(bounds[0]) > Long.parseLong(bounds[1]);
    }
  }
}
