package dev.ward.infrastructure.dependency.node;

/** Turns JSON with comments and trailing commas, as {@code bun.lock} is written, into JSON. */
final class Jsonc {
  private Jsonc() {}

  /** Drops comments and trailing commas outside strings. */
  static String toJson(final String text) {
    final StringBuilder out = new StringBuilder(text.length());
    int index = 0;
    while (index < text.length()) {
      final char current = text.charAt(index);
      if (current == '"') {
        index = copyString(text, index, out);
      } else if (text.startsWith("//", index)) {
        index = text.indexOf('\n', index);
        index = index < 0 ? text.length() : index;
      } else if (text.startsWith("/*", index)) {
        final int end = text.indexOf("*/", index + 2);
        index = end < 0 ? text.length() : end + 2;
      } else if ((current == '}' || current == ']') && trimTrailingComma(out)) {
        out.append(current);
        index++;
      } else {
        out.append(current);
        index++;
      }
    }
    return out.toString();
  }

  private static int copyString(final String text, final int start, final StringBuilder out) {
    int index = start;
    out.append(text.charAt(index++));
    while (index < text.length()) {
      final char current = text.charAt(index);
      out.append(current);
      index++;
      if (current == '\\' && index < text.length()) {
        out.append(text.charAt(index++));
      } else if (current == '"') {
        break;
      }
    }
    return index;
  }

  /** Removes a comma that ends {@code out} before a closing bracket; always returns true. */
  private static boolean trimTrailingComma(final StringBuilder out) {
    int end = out.length();
    while (end > 0 && Character.isWhitespace(out.charAt(end - 1))) {
      end--;
    }
    if (end > 0 && out.charAt(end - 1) == ',') {
      out.deleteCharAt(end - 1);
    }
    return true;
  }
}
