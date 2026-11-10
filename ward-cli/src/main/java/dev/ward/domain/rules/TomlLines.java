package dev.ward.domain.rules;

/** Scans one line of TOML text: where its comment starts, its code, and its bracket depth. */
final class TomlLines {
  private static final char QUOTE = '"';
  private static final char COMMENT = '#';
  private static final char ESCAPE = '\\';
  private static final char OPEN = '[';
  private static final char CLOSE = ']';

  private TomlLines() {}

  /** Returns the index of the first comment character outside a string, or the line length. */
  static int commentStart(final String line) {
    boolean quoted = false;
    boolean escaped = false;
    for (int i = 0; i < line.length(); i++) {
      final char c = line.charAt(i);
      if (escaped) {
        escaped = false;
      } else if (quoted && c == ESCAPE) {
        escaped = true;
      } else if (c == QUOTE) {
        quoted = !quoted;
      } else if (!quoted && c == COMMENT) {
        return i;
      }
    }
    return line.length();
  }

  /** Returns the part of the line before its comment. */
  static String codeOf(final String line) {
    return line.substring(0, commentStart(line));
  }

  /** Returns the number of opening brackets minus the closing ones, outside strings. */
  static int depthChange(final String line) {
    int change = 0;
    boolean quoted = false;
    boolean escaped = false;
    for (final char c : codeOf(line).toCharArray()) {
      if (escaped) {
        escaped = false;
      } else if (quoted && c == ESCAPE) {
        escaped = true;
      } else if (c == QUOTE) {
        quoted = !quoted;
      } else if (!quoted) {
        change += bracket(c);
      }
    }
    return change;
  }

  private static int bracket(final char c) {
    if (c == OPEN) {
      return 1;
    }
    return c == CLOSE ? -1 : 0;
  }
}
