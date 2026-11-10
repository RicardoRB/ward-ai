package dev.ward.infrastructure.analysis.node;

/** Turns the JSON-with-comments of a tsconfig into plain JSON: comments and trailing commas go. */
final class JsoncText {
  private static final char QUOTE = '"';
  private static final char BACKSLASH = '\\';
  private static final char COMMA = ',';
  private static final char CLOSE_OBJECT = '}';
  private static final char CLOSE_ARRAY = ']';
  private static final char NEWLINE = '\n';
  private static final String LINE_COMMENT = "//";
  private static final String BLOCK_OPEN = "/*";
  private static final String BLOCK_CLOSE = "*/";

  private JsoncText() {}

  /** Returns {@code text} without comments and without commas before a closing bracket. */
  static String plain(final String text) {
    return withoutTrailingCommas(withoutComments(text));
  }

  private static String withoutComments(final String text) {
    final StringBuilder out = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      i = text.charAt(i) == QUOTE ? copyString(text, i, out) : skipOrCopy(text, i, out);
    }
    return out.toString();
  }

  private static String withoutTrailingCommas(final String text) {
    final StringBuilder out = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      if (text.charAt(i) == QUOTE) {
        i = copyString(text, i, out);
      } else {
        if (text.charAt(i) != COMMA || !closesNext(text, i + 1)) {
          out.append(text.charAt(i));
        }
        i++;
      }
    }
    return out.toString();
  }

  /** Copies the string literal that starts at {@code start}, and returns the index after it. */
  private static int copyString(final String text, final int start, final StringBuilder out) {
    int i = start;
    out.append(text.charAt(i));
    i++;
    while (i < text.length()) {
      final char c = text.charAt(i);
      out.append(c);
      i++;
      if (c == BACKSLASH && i < text.length()) {
        out.append(text.charAt(i));
        i++;
      } else if (c == QUOTE) {
        break;
      }
    }
    return i;
  }

  /** Skips a comment at {@code i}, or copies the character there; returns the next index. */
  private static int skipOrCopy(final String text, final int i, final StringBuilder out) {
    if (text.startsWith(LINE_COMMENT, i)) {
      final int newline = text.indexOf(NEWLINE, i);
      return newline < 0 ? text.length() : newline;
    }
    if (text.startsWith(BLOCK_OPEN, i)) {
      final int end = text.indexOf(BLOCK_CLOSE, i + BLOCK_OPEN.length());
      return end < 0 ? text.length() : end + BLOCK_CLOSE.length();
    }
    out.append(text.charAt(i));
    return i + 1;
  }

  private static boolean closesNext(final String text, final int from) {
    for (int i = from; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (!Character.isWhitespace(c)) {
        return c == CLOSE_OBJECT || c == CLOSE_ARRAY;
      }
    }
    return false;
  }
}
