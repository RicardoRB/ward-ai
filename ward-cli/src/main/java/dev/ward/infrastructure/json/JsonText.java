package dev.ward.infrastructure.json;

import dev.ward.domain.config.WardException;

/**
 * Character-level reading of JSON text: string ends, whitespace, nested values, and comments read
 * as blank space. Indices refer to the same positions in the text and in its {@link #mask masked}
 * copy.
 */
public final class JsonText {
  static final char QUOTE = '"';
  static final char COMMA = ',';
  static final char COLON = ':';
  private static final char ESCAPE = '\\';
  private static final char NEWLINE = '\n';
  private static final String OPENERS = "{[";
  private static final String CLOSERS = "}]";

  private JsonText() {}

  /** Returns the index of the quote that closes the string starting at {@code start}. */
  static int stringEnd(final String content, final int start) {
    int end = start + 1;
    while (end < content.length() && content.charAt(end) != QUOTE) {
      end += content.charAt(end) == ESCAPE ? 2 : 1;
    }
    if (end >= content.length()) {
      throw malformed();
    }
    return end;
  }

  /** Returns the index of the first non-whitespace character at or after {@code from}. */
  static int skipWhitespace(final String content, final int from) {
    int index = from;
    while (index < content.length() && Character.isWhitespace(content.charAt(index))) {
      index++;
    }
    return index;
  }

  /**
   * Returns the index of the first character at or after {@code from} that is neither space nor
   * comma.
   */
  static int skipSeparators(final String content, final int from, final int limit) {
    int index = from;
    while (index < limit
        && (Character.isWhitespace(content.charAt(index)) || content.charAt(index) == COMMA)) {
      index++;
    }
    return index;
  }

  /** Returns the index of the last character of the JSON value that starts at {@code start}. */
  static int valueEnd(final String content, final int start) {
    final char first = content.charAt(start);
    if (first == QUOTE) {
      return stringEnd(content, start);
    }
    if (OPENERS.indexOf(first) >= 0) {
      return containerEnd(content, start);
    }
    int end = start;
    while (end + 1 < content.length()
        && content.charAt(end + 1) != COMMA
        && CLOSERS.indexOf(content.charAt(end + 1)) < 0
        && !Character.isWhitespace(content.charAt(end + 1))) {
      end++;
    }
    return end;
  }

  /** Returns the index of the bracket that closes the object or array opened at {@code start}. */
  private static int containerEnd(final String content, final int start) {
    int depth = 0;
    int index = start;
    while (index < content.length()) {
      final char next = content.charAt(index);
      if (next == QUOTE) {
        index = stringEnd(content, index);
      } else if (OPENERS.indexOf(next) >= 0) {
        depth++;
      } else if (CLOSERS.indexOf(next) >= 0) {
        depth--;
        if (depth == 0) {
          return index;
        }
      }
      index++;
    }
    throw malformed();
  }

  /**
   * Returns {@code content} with each comment replaced by blanks, keeping every line break and
   * index. Comment markers inside strings are kept.
   */
  public static String mask(final String content) {
    final StringBuilder masked = new StringBuilder(content);
    int index = 0;
    while (index < content.length()) {
      final char next = content.charAt(index);
      if (next == QUOTE) {
        index = stringEnd(content, index) + 1;
      } else if (content.startsWith("//", index)) {
        final int line = content.indexOf(NEWLINE, index);
        final int end = line < 0 ? content.length() : line;
        blank(masked, index, end);
        index = end;
      } else if (content.startsWith("/*", index)) {
        final int close = content.indexOf("*/", index + 2);
        final int end = close < 0 ? content.length() : close + 2;
        blank(masked, index, end);
        index = end;
      } else {
        index++;
      }
    }
    return masked.toString();
  }

  private static void blank(final StringBuilder text, final int from, final int to) {
    for (int i = from; i < to; i++) {
      if (text.charAt(i) != NEWLINE) {
        text.setCharAt(i, ' ');
      }
    }
  }

  /** Returns the error for a file whose layout Ward cannot edit safely. */
  static WardException malformed() {
    return new WardException(
        "Ward cannot edit this JSON file: its layout cannot be located."
            + " Format it as a multi-line JSON object, then run init again.");
  }
}
