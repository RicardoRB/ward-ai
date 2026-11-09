package dev.ward.analysis.domain.rule.check;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A call of a plain name, such as {@code f(a, b)}, whose text is exactly that call. */
record PlainCall(String callee, String arguments) {

  private static final Pattern START = Pattern.compile("^(\\w+)\\(");
  private static final int NONE = -1;
  private static final char OPEN = '(';
  private static final char CLOSE = ')';

  /** Reads the text of a call; empty when the text is anything else, such as {@code f(a).b}. */
  static Optional<PlainCall> of(final String text) {
    final Matcher start = START.matcher(text);
    if (!start.find()) {
      return Optional.empty();
    }
    final int open = start.end() - 1;
    final int close = closing(text, open);
    return close == text.length() - 1
        ? Optional.of(new PlainCall(start.group(1), text.substring(open + 1, close)))
        : Optional.empty();
  }

  /** The index of the parenthesis that closes the one at {@code open}, or -1. */
  private static int closing(final String text, final int open) {
    int depth = 0;
    for (int index = open; index < text.length(); index++) {
      final char c = text.charAt(index);
      if (c == OPEN) {
        depth++;
      } else if (c == CLOSE) {
        depth--;
        if (depth == 0) {
          return index;
        }
      }
    }
    return NONE;
  }
}
