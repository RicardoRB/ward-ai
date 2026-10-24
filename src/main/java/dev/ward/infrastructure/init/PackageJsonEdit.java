package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import java.util.List;
import java.util.Map;

/**
 * Inserts dev dependencies into {@code package.json} text without re-serializing it, so every byte
 * outside the inserted entries is unchanged.
 */
final class PackageJsonEdit {
  private static final String DEV_DEPENDENCIES = "devDependencies";
  private static final String DEFAULT_INDENT = "  ";
  private static final char QUOTE = '"';
  private static final char NEWLINE = '\n';
  private static final String OPENERS = "{[";
  private static final String CLOSERS = "}]";
  // A pending devDependencies key survives the colon between it and its value.
  private static final String SEPARATORS = ":,";

  private PackageJsonEdit() {}

  /**
   * Returns {@code content} with {@code entries} (name to exact version) appended to {@code
   * devDependencies}, creating the object when absent.
   *
   * @throws WardException if the file is not a multi-line JSON object whose layout can be located
   */
  static String addDevDependencies(final String content, final Map<String, String> entries) {
    final Layout layout = Layout.of(content);
    final String newline = content.contains("\r\n") ? "\r\n" : "\n";
    if (layout.devOpen() < 0) {
      final String indent = layout.indent();
      final String object =
          quoted(DEV_DEPENDENCIES)
              + ": {"
              + newline
              + lines(entries, indent + indent, newline)
              + newline
              + indent
              + "}";
      return insertLast(content, layout.rootOpen(), layout.rootClose(), indent + object, newline);
    }
    final String entryIndent = layout.entryIndent(content);
    return insertLast(
        content,
        layout.devOpen(),
        layout.devClose(),
        lines(entries, entryIndent, newline),
        newline);
  }

  /** Inserts {@code text} as the last member of the object spanning {@code open}..{@code close}. */
  private static String insertLast(
      final String content,
      final int open,
      final int close,
      final String text,
      final String newline) {
    int last = close - 1;
    while (last > open && Character.isWhitespace(content.charAt(last))) {
      last--;
    }
    final boolean empty = last == open;
    final String closingIndent = indentOfLine(content, open);
    final String before = content.substring(0, last + 1);
    return before
        + (empty ? "" : ",")
        + newline
        + text
        + newline
        + closingIndent
        + content.substring(close);
  }

  private static String lines(
      final Map<String, String> entries, final String indent, final String newline) {
    final List<String> lines =
        entries.entrySet().stream()
            .map(entry -> indent + quoted(entry.getKey()) + ": " + quoted(entry.getValue()))
            .toList();
    return String.join("," + newline, lines);
  }

  /** The whitespace that starts the line containing {@code index}. */
  private static String indentOfLine(final String content, final int index) {
    final int start = content.lastIndexOf('\n', index) + 1;
    int end = start;
    while (end < index && (content.charAt(end) == ' ' || content.charAt(end) == '\t')) {
      end++;
    }
    return content.substring(start, end);
  }

  private static String quoted(final String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  /**
   * Where the top-level object and its {@code devDependencies} object start and end.
   *
   * @param rootOpen the index of the top-level {@code {}
   * @param rootClose the index of the top-level {@code }}
   * @param devOpen the index of {@code devDependencies}' {@code {}, or -1 when absent
   * @param devClose the index of its {@code }}, or -1 when absent
   * @param indent the file's indentation unit
   */
  private record Layout(int rootOpen, int rootClose, int devOpen, int devClose, String indent) {

    static Layout of(final String content) {
      final Scanner scanner = new Scanner(content);
      scanner.scan();
      if (scanner.rootOpen < 0
          || scanner.rootClose < 0
          || scanner.devOpen >= 0 && scanner.devClose < 0
          || !content.substring(scanner.rootOpen, scanner.rootClose).contains("\n")) {
        throw new WardException(
            "Ward cannot add dev dependencies to package.json: its layout cannot be located."
                + " Format it as a multi-line JSON object, then run init again.");
      }
      return new Layout(
          scanner.rootOpen,
          scanner.rootClose,
          scanner.devOpen,
          scanner.devClose,
          scanner.indent == null ? DEFAULT_INDENT : scanner.indent);
    }

    /** The indentation of the existing entries, or one level deeper than the key when empty. */
    String entryIndent(final String content) {
      if (content.substring(devOpen + 1, devClose).isBlank()) {
        return indentOfLine(content, devOpen) + indent;
      }
      final int newline = content.indexOf('\n', devOpen);
      if (newline < 0 || newline > devClose) {
        throw new WardException(
            "Ward cannot add dev dependencies to package.json: devDependencies is written on one"
                + " line. Put each entry on its own line, then run init again.");
      }
      int end = newline + 1;
      while (end < devClose && (content.charAt(end) == ' ' || content.charAt(end) == '\t')) {
        end++;
      }
      return content.substring(newline + 1, end);
    }
  }

  /** Finds the top-level object and the top-level {@code devDependencies} value. */
  private static final class Scanner {
    private final String content;
    private int position;
    private int depth;
    private int rootOpen = -1;
    private int rootClose = -1;
    private int devOpen = -1;
    private int devClose = -1;
    private int devDepth = -1;
    private String indent;
    private boolean nextIsDev;

    Scanner(final String content) {
      this.content = content;
    }

    void scan() {
      while (position < content.length() && rootClose < 0) {
        final char next = content.charAt(position);
        if (next == QUOTE) {
          string();
        } else {
          structural(next);
          position++;
        }
      }
    }

    private void structural(final char next) {
      if (OPENERS.indexOf(next) >= 0) {
        open(next);
      } else if (CLOSERS.indexOf(next) >= 0) {
        close(next);
      } else if (next == NEWLINE) {
        newline();
      } else if (!Character.isWhitespace(next) && SEPARATORS.indexOf(next) < 0) {
        nextIsDev = false;
      }
    }

    private void newline() {
      if (depth == 1 && indent == null) {
        indent = leadingWhitespace(position + 1);
      }
    }

    private void close(final char next) {
      depth--;
      if (next == '}' && depth == devDepth) {
        devClose = position;
        devDepth = -1;
      }
      if (next == '}' && depth == 0) {
        rootClose = position;
      }
    }

    private void open(final char next) {
      if (depth == 0 && next == '{' && rootOpen < 0) {
        rootOpen = position;
      }
      if (nextIsDev && next == '{' && depth == 1) {
        devOpen = position;
        devDepth = depth;
      }
      nextIsDev = false;
      depth++;
    }

    private void string() {
      final int start = position + 1;
      position = start;
      while (position < content.length() && content.charAt(position) != '"') {
        position += content.charAt(position) == '\\' ? 2 : 1;
      }
      final String value = content.substring(start, Math.min(position, content.length()));
      position++;
      nextIsDev = depth == 1 && DEV_DEPENDENCIES.equals(value) && followedByColon();
    }

    private boolean followedByColon() {
      int index = position;
      while (index < content.length() && Character.isWhitespace(content.charAt(index))) {
        index++;
      }
      return index < content.length() && content.charAt(index) == ':';
    }

    private String leadingWhitespace(final int from) {
      int end = from;
      while (end < content.length()
          && (content.charAt(end) == ' ' || content.charAt(end) == '\t')) {
        end++;
      }
      return end > from && end < content.length() && content.charAt(end) == '"'
          ? content.substring(from, end)
          : null;
    }
  }
}
