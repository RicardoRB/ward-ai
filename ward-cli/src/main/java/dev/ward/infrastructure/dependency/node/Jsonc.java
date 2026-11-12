package dev.ward.infrastructure.dependency.node;

/** Turns JSON with comments and trailing commas, as {@code bun.lock} is written, into JSON. */
final class Jsonc {
  private static final char QUOTE = '"';
  private static final char ESCAPE = '\\';
  private static final char COMMA = ',';
  private static final char CLOSE_OBJECT = '}';
  private static final char CLOSE_ARRAY = ']';
  private static final String LINE_COMMENT = "//";
  private static final String BLOCK_START = "/*";
  private static final String BLOCK_END = "*/";

  private Jsonc() {}

  /** Drops comments and trailing commas outside strings. */
  static String toJson(final String text) {
    final StringBuilder out = new StringBuilder(text.length());
    int index = 0;
    while (index < text.length()) {
      index = step(text, index, out);
    }
    return out.toString();
  }

  /** Copies or skips what starts at {@code index} and returns where the next step starts. */
  private static int step(final String text, final int index, final StringBuilder out) {
    final char current = text.charAt(index);
    if (current == QUOTE) {
      return copyString(text, index, out);
    }
    if (text.startsWith(LINE_COMMENT, index)) {
      final int end = text.indexOf('\n', index);
      return end < 0 ? text.length() : end;
    }
    if (text.startsWith(BLOCK_START, index)) {
      final int end = text.indexOf(BLOCK_END, index + BLOCK_START.length());
      return end < 0 ? text.length() : end + BLOCK_END.length();
    }
    if (current == CLOSE_OBJECT || current == CLOSE_ARRAY) {
      trimTrailingComma(out);
    }
    out.append(current);
    return index + 1;
  }

  private static int copyString(final String text, final int start, final StringBuilder out) {
    out.append(QUOTE);
    int index = start + 1;
    while (index < text.length()) {
      final char current = text.charAt(index);
      out.append(current);
      index++;
      if (current == ESCAPE && index < text.length()) {
        out.append(text.charAt(index));
        index++;
      } else if (current == QUOTE) {
        break;
      }
    }
    return index;
  }

  /** Removes a comma that ends {@code out} before a closing bracket. */
  private static void trimTrailingComma(final StringBuilder out) {
    int end = out.length();
    while (end > 0 && Character.isWhitespace(out.charAt(end - 1))) {
      end--;
    }
    if (end > 0 && out.charAt(end - 1) == COMMA) {
      out.deleteCharAt(end - 1);
    }
  }
}
