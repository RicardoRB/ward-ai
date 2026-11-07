package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The shape of the right operand of a {@code %} operator when it is a literal: a tuple with a known
 * number of items, a dictionary with its string keys, or a single string. Any other operand has no
 * shape.
 *
 * @param kind the kind of operand
 * @param count the number of items of a tuple
 * @param keys the string keys of a dictionary, or empty when a key is not a string literal
 */
record PercentArgument(Kind kind, int count, Optional<List<String>> keys) {

  /** The kinds of operand that the checks tell apart. */
  enum Kind {
    TUPLE,
    DICT,
    STRING,
    OTHER
  }

  private static final char OPEN_PAREN = '(';
  private static final char CLOSE_PAREN = ')';
  private static final char OPEN_BRACE = '{';
  private static final char SEPARATOR = ',';
  private static final char KEY_SEPARATOR = ':';
  private static final char SINGLE_QUOTE = '\'';
  private static final char DOUBLE_QUOTE = '"';

  /** Copies the keys. */
  PercentArgument {
    keys = keys.map(List::copyOf);
  }

  /** Reads the operand from its text, with the whitespace the tree text does not keep. */
  static PercentArgument of(final String text) {
    if (isStringLiteral(text)) {
      return new PercentArgument(Kind.STRING, 0, Optional.empty());
    }
    if (enclosedBy(text, OPEN_PAREN)) {
      return tuple(text.substring(1, text.length() - 1));
    }
    if (enclosedBy(text, OPEN_BRACE)) {
      return dict(text.substring(1, text.length() - 1));
    }
    return other();
  }

  /** Splits {@code text} at {@code separator} outside brackets and string literals. */
  static List<String> split(final String text, final char separator) {
    final List<String> parts = new ArrayList<>();
    final Nesting nesting = new Nesting();
    int start = 0;
    for (int index = 0; index < text.length(); index++) {
      final char current = text.charAt(index);
      if (nesting.topLevel(current) && current == separator) {
        parts.add(text.substring(start, index));
        start = index + 1;
      }
    }
    parts.add(text.substring(start));
    return parts;
  }

  private static PercentArgument other() {
    return new PercentArgument(Kind.OTHER, 0, Optional.empty());
  }

  private static boolean enclosedBy(final String text, final char open) {
    final char close = open == OPEN_PAREN ? CLOSE_PAREN : '}';
    return text.length() >= 2
        && text.charAt(0) == open
        && matchingClose(text) == text.length() - 1
        && text.charAt(text.length() - 1) == close;
  }

  private static int matchingClose(final String text) {
    final Nesting nesting = new Nesting();
    for (int index = 0; index < text.length(); index++) {
      nesting.topLevel(text.charAt(index));
      if (nesting.depth() == 0) {
        return index;
      }
    }
    return -1;
  }

  private static PercentArgument tuple(final String inner) {
    if (inner.isEmpty()) {
      return new PercentArgument(Kind.TUPLE, 0, Optional.empty());
    }
    final List<String> items = split(inner, SEPARATOR);
    final boolean trailingComma = inner.endsWith(",");
    if (isNotTuple(items, trailingComma)) {
      return other();
    }
    final int count = trailingComma ? items.size() - 1 : items.size();
    return new PercentArgument(Kind.TUPLE, count, Optional.empty());
  }

  private static boolean isNotTuple(final List<String> items, final boolean trailingComma) {
    return (!trailingComma && items.size() == 1)
        || items.stream().anyMatch(item -> item.startsWith("*"));
  }

  private static PercentArgument dict(final String inner) {
    final List<String> keys = new ArrayList<>();
    for (final String item : split(inner, SEPARATOR)) {
      if (!item.isEmpty()) {
        final String key = split(item, KEY_SEPARATOR).get(0);
        if (!isStringLiteral(key)) {
          return new PercentArgument(Kind.DICT, 0, Optional.empty());
        }
        keys.add(key.substring(1, key.length() - 1));
      }
    }
    return new PercentArgument(Kind.DICT, 0, Optional.of(keys));
  }

  private static boolean isStringLiteral(final String text) {
    return text.length() >= 2
        && (text.charAt(0) == DOUBLE_QUOTE || text.charAt(0) == SINGLE_QUOTE)
        && text.charAt(text.length() - 1) == text.charAt(0);
  }

  /** Tracks brackets and string literals while a text is read from left to right. */
  private static final class Nesting {

    private static final String OPENERS = "({[";
    private static final String CLOSERS = ")}]";

    private int depth;
    private char quote;

    /** Reads one character and returns whether it is outside every bracket and literal. */
    boolean topLevel(final char current) {
      final boolean outside = quote == 0 && depth == 0;
      if (quote == 0) {
        open(current);
      } else if (current == quote) {
        quote = 0;
      }
      return outside;
    }

    private void open(final char current) {
      if (current == DOUBLE_QUOTE || current == SINGLE_QUOTE) {
        quote = current;
      } else if (OPENERS.indexOf(current) >= 0) {
        depth++;
      } else if (CLOSERS.indexOf(current) >= 0) {
        depth--;
      }
    }

    int depth() {
      return depth;
    }
  }
}
