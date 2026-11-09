package dev.ward.analysis.domain.rule.check;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Sequences and mappings written as literals in a call's arguments. */
final class LiteralSequences {

  private static final char OPEN_BRACE = '{';
  private static final char CLOSE_BRACE = '}';
  private static final char OPEN_BRACKET = '[';
  private static final char CLOSE_BRACKET = ']';
  private static final char OPEN_PAREN = '(';
  private static final char CLOSE_PAREN = ')';
  private static final char COMMA = ',';
  private static final char SINGLE_QUOTE = '\'';
  private static final char DOUBLE_QUOTE = '"';

  private LiteralSequences() {}

  static Optional<List<String>> sequence(final String value) {
    if (!bracketed(value, OPEN_BRACKET, CLOSE_BRACKET)
        && !(bracketed(value, OPEN_PAREN, CLOSE_PAREN) && value.indexOf(COMMA) >= 0)) {
      return Optional.empty();
    }
    return Optional.of(ArgumentList.split(value.substring(1, value.length() - 1)));
  }

  static Optional<Set<String>> dictKeys(final String value) {
    if (!bracketed(value, OPEN_BRACE, CLOSE_BRACE)) {
      return Optional.empty();
    }
    final Set<String> keys = new HashSet<>();
    for (final String entry : ArgumentList.split(value.substring(1, value.length() - 1))) {
      final int colon = entry.indexOf(':');
      final String key = colon < 0 ? "" : entry.substring(0, colon).strip();
      if (!isQuoted(key)) {
        return Optional.empty();
      }
      keys.add(ArgumentList.unquote(key));
    }
    return Optional.of(keys);
  }

  static boolean isQuoted(final String text) {
    return text.length() >= 2 && (text.charAt(0) == DOUBLE_QUOTE || text.charAt(0) == SINGLE_QUOTE);
  }

  static boolean bracketed(final String value, final char open, final char close) {
    return value.length() >= 2
        && value.charAt(0) == open
        && value.charAt(value.length() - 1) == close;
  }
}
