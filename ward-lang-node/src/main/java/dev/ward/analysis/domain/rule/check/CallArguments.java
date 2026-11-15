package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The arguments of a call's source text, split at the top-level commas, with quotes and nesting
 * respected.
 */
final class CallArguments {

  private static final char CLOSE = ')';
  private static final char COMMA = ',';
  private static final String OPENERS = "([{";
  private static final String CLOSERS = ")]}";
  private static final String QUOTES = "'\"`";

  private CallArguments() {}

  /** The argument at {@code index} of a call whose text continues after its opening parenthesis. */
  static Optional<String> at(final String afterOpen, final int index) {
    final List<String> arguments = split(untilClose(afterOpen));
    return index < arguments.size() ? Optional.of(arguments.get(index)) : Optional.empty();
  }

  /** The text up to the parenthesis that closes the call, or the whole text when none does. */
  private static String untilClose(final String text) {
    char quote = 0;
    int depth = 0;
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (atTopLevel(quote, depth, c, CLOSE)) {
        return text.substring(0, i);
      }
      quote = nextQuote(quote, c);
      depth += quote == 0 ? nesting(c) : 0;
    }
    return text;
  }

  private static boolean atTopLevel(
      final char quote, final int depth, final char c, final char wanted) {
    return quote == 0 && depth == 0 && c == wanted;
  }

  private static List<String> split(final String list) {
    final List<String> arguments = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    char quote = 0;
    int depth = 0;
    for (final char c : list.toCharArray()) {
      if (atTopLevel(quote, depth, c, COMMA)) {
        arguments.add(current.toString().trim());
        current.setLength(0);
      } else {
        current.append(c);
        quote = nextQuote(quote, c);
        depth += quote == 0 ? nesting(c) : 0;
      }
    }
    arguments.add(current.toString().trim());
    return arguments;
  }

  /** The quote that is open after reading {@code c}, given the quote that was open before it. */
  private static char nextQuote(final char open, final char c) {
    if (open != 0) {
      return c == open ? 0 : open;
    }
    return QUOTES.indexOf(c) >= 0 ? c : 0;
  }

  /**
   * The change in nesting depth of a character: +1 for an opening bracket, -1 for a closing one.
   */
  private static int nesting(final char c) {
    if (OPENERS.indexOf(c) >= 0) {
      return 1;
    }
    return CLOSERS.indexOf(c) >= 0 ? -1 : 0;
  }
}
