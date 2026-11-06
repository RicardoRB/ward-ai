package dev.ward.analysis.domain.rule.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The shape of the right operand of a {@code %} operator when it is a literal: a tuple with a known
 * number of items, or a dictionary with its string keys. Any other operand has no shape.
 */
final class PercentArgument {

  /** The kinds of operand that the checks tell apart. */
  enum Kind {
    TUPLE,
    DICT,
    OTHER
  }

  private static final char OPEN_PAREN = '(';
  private static final char CLOSE_PAREN = ')';
  private static final char OPEN_BRACE = '{';
  private static final char CLOSE_BRACE = '}';
  private static final char SEPARATOR = ',';
  private static final char KEY_SEPARATOR = ':';

  private final Kind kind;
  private final int count;
  private final Optional<List<String>> keys;

  private PercentArgument(final Kind kind, final int count, final Optional<List<String>> keys) {
    this.kind = kind;
    this.count = count;
    this.keys = keys;
  }

  /** Reads the operand from its text, with the whitespace the tree text does not keep. */
  static PercentArgument of(final String text) {
    if (text.length() >= 2 && text.charAt(0) == OPEN_PAREN && closesAtEnd(text)) {
      return tuple(text.substring(1, text.length() - 1));
    }
    if (text.length() >= 2 && text.charAt(0) == OPEN_BRACE && closesAtEnd(text)) {
      return dict(text.substring(1, text.length() - 1));
    }
    return new PercentArgument(Kind.OTHER, 0, Optional.empty());
  }

  /** The kind of operand. */
  Kind kind() {
    return kind;
  }

  /** The number of items of a tuple. */
  int count() {
    return count;
  }

  /** The string keys of a dictionary, or empty when a key is not a string literal. */
  Optional<List<String>> keys() {
    return keys;
  }

  private static boolean closesAtEnd(final String text) {
    final char open = text.charAt(0);
    final char close = open == OPEN_PAREN ? CLOSE_PAREN : CLOSE_BRACE;
    return matchingClose(text, 0) == text.length() - 1 && text.charAt(text.length() - 1) == close;
  }

  private static PercentArgument tuple(final String inner) {
    if (inner.isEmpty()) {
      return new PercentArgument(Kind.TUPLE, 0, Optional.empty());
    }
    final List<String> items = split(inner, SEPARATOR);
    if (inner.endsWith(",") || items.size() > 1) {
      final boolean star = items.stream().anyMatch(item -> item.startsWith("*"));
      final int count = inner.endsWith(",") ? items.size() - 1 : items.size();
      return star
          ? new PercentArgument(Kind.OTHER, 0, Optional.empty())
          : new PercentArgument(Kind.TUPLE, count, Optional.empty());
    }
    return new PercentArgument(Kind.OTHER, 0, Optional.empty());
  }

  private static PercentArgument dict(final String inner) {
    final List<String> keys = new ArrayList<>();
    for (final String item : split(inner, SEPARATOR)) {
      if (item.isEmpty()) {
        continue;
      }
      final String key = keyOf(item);
      if (!isStringLiteral(key)) {
        return new PercentArgument(Kind.DICT, 0, Optional.empty());
      }
      keys.add(key.substring(1, key.length() - 1));
    }
    return new PercentArgument(Kind.DICT, 0, Optional.of(List.copyOf(keys)));
  }

  private static String keyOf(final String item) {
    final List<String> parts = split(item, KEY_SEPARATOR);
    return parts.get(0);
  }

  private static boolean isStringLiteral(final String text) {
    return text.length() >= 2
        && (text.charAt(0) == '"' || text.charAt(0) == '\'')
        && text.charAt(text.length() - 1) == text.charAt(0);
  }

  /** Splits {@code text} at {@code separator} outside brackets and string literals. */
  static List<String> split(final String text, final char separator) {
    final List<String> parts = new ArrayList<>();
    int depth = 0;
    char quote = 0;
    int start = 0;
    for (int index = 0; index < text.length(); index++) {
      final char current = text.charAt(index);
      if (quote != 0) {
        quote = current == quote ? 0 : quote;
      } else if (current == '"' || current == '\'') {
        quote = current;
      } else if (current == OPEN_PAREN || current == OPEN_BRACE || current == '[') {
        depth++;
      } else if (current == CLOSE_PAREN || current == CLOSE_BRACE || current == ']') {
        depth--;
      } else if (current == separator && depth == 0) {
        parts.add(text.substring(start, index));
        start = index + 1;
      }
    }
    parts.add(text.substring(start));
    return parts;
  }

  private static int matchingClose(final String text, final int open) {
    int depth = 0;
    char quote = 0;
    for (int index = open; index < text.length(); index++) {
      final char current = text.charAt(index);
      if (quote != 0) {
        quote = current == quote ? 0 : quote;
      } else if (current == '"' || current == '\'') {
        quote = current;
      } else if (current == OPEN_PAREN || current == OPEN_BRACE || current == '[') {
        depth++;
      } else if (current == CLOSE_PAREN || current == CLOSE_BRACE || current == ']') {
        depth--;
        if (depth == 0) {
          return index;
        }
      }
    }
    return -1;
  }
}
