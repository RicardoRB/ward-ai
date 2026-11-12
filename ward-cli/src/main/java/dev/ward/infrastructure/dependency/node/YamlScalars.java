package dev.ward.infrastructure.dependency.node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Scalar-level helpers of the YAML subset reader: quotes, comments, keys and typed access. */
final class YamlScalars {
  private static final char SINGLE = '\'';
  private static final char DOUBLE = '"';
  private static final char BACKSLASH = '\\';
  private static final char COLON = ':';
  private static final char SPACE = ' ';
  private static final String COMMENT = " #";

  private YamlScalars() {}

  /** Returns a plain or quoted scalar without its quotes and without a trailing comment. */
  static String plain(final String text) {
    if (quoted(text)) {
      final int close = closingQuote(text, 0, text.charAt(0));
      return unquote(close < 0 ? text : text.substring(0, close + 1));
    }
    final int comment = text.indexOf(COMMENT);
    return unquote(comment < 0 ? text : text.substring(0, comment).strip());
  }

  /** Returns where the key of {@code key: value} ends, or empty when there is no key. */
  static OptionalInt keyEnd(final String content) {
    final boolean quoted = quoted(content);
    final int from = quoted ? closingQuote(content, 0, content.charAt(0)) + 1 : 0;
    if (quoted && from == 0) {
      return OptionalInt.empty();
    }
    for (int index = from; index < content.length(); index++) {
      if (isSeparator(content, index)) {
        return OptionalInt.of(index);
      }
      if (quoted) {
        break;
      }
    }
    return OptionalInt.empty();
  }

  /** Returns whether {@code text} starts with a quote. */
  static boolean quoted(final String text) {
    return text.startsWith("'") || text.startsWith("\"");
  }

  /** A colon ends a key only at the end of the line or before a space. */
  private static boolean isSeparator(final String content, final int index) {
    return content.charAt(index) == COLON
        && (index + 1 == content.length() || content.charAt(index + 1) == SPACE);
  }

  /** Returns the index of the quote that closes the one at {@code open}, or -1. */
  static int closingQuote(final String text, final int open, final char quote) {
    int index = open + 1;
    while (index < text.length()) {
      final char current = text.charAt(index);
      if (current == BACKSLASH && quote == DOUBLE) {
        index += 2;
      } else if (current == quote) {
        if (quote == SINGLE && index + 1 < text.length() && text.charAt(index + 1) == SINGLE) {
          index += 2;
        } else {
          return index;
        }
      } else {
        index++;
      }
    }
    return -1;
  }

  /** Returns {@code value} as a map, or an empty map when it is not one. */
  static Map<String, Object> map(final Object value) {
    final Map<String, Object> copy = new LinkedHashMap<>();
    if (value instanceof Map<?, ?> source) {
      source.forEach((key, item) -> copy.put(String.valueOf(key), item));
    }
    return copy;
  }

  /** Returns {@code value} as text, or an empty text when it is not a scalar. */
  static String text(final Object value) {
    return value instanceof String text ? text : "";
  }

  /** Removes one pair of surrounding quotes and what they escape. */
  static String unquote(final String text) {
    if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
      return text.substring(1, text.length() - 1).replace("''", "'");
    }
    if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
      return text.substring(1, text.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
    return text;
  }
}
