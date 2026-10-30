package dev.ward.infrastructure.init;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where a JSON file's top-level object starts and ends, and where each top-level member that is an
 * object sits. Read from masked text, so comments are ignored.
 *
 * @param rootOpen the index of the top-level {@code {}
 * @param rootClose the index of the top-level {@code }}
 * @param objects the top-level members whose values are objects, by name
 * @param indent the file's indentation unit, when a member line shows one
 */
record JsonLayout(
    int rootOpen, int rootClose, Map<String, JsonSpan> objects, Optional<String> indent) {
  private static final String DEFAULT_INDENT = "  ";
  private static final char OPEN_OBJECT = '{';
  private static final char CLOSE_OBJECT = '}';
  private static final char NEWLINE = '\n';
  private static final String OPENERS = "{[";
  private static final String CLOSERS = "}]";

  /** Reads the layout of {@code masked}; a file it cannot locate is a {@code WardException}. */
  static JsonLayout of(final String masked) {
    final Scanner scanner = new Scanner(masked);
    scanner.scan();
    if (scanner.rootOpen < 0
        || scanner.rootClose < 0
        || !masked.substring(scanner.rootOpen, scanner.rootClose).contains("\n")) {
      throw JsonText.malformed();
    }
    return new JsonLayout(scanner.rootOpen, scanner.rootClose, scanner.objects, scanner.indent);
  }

  /** Returns the top-level object member {@code key}, when the file has one. */
  Optional<JsonSpan> member(final String key) {
    return Optional.ofNullable(objects.get(key));
  }

  /** Returns the indentation unit, or two spaces when the file shows none. */
  String indentUnit() {
    return indent.orElse(DEFAULT_INDENT);
  }

  /** Finds the top-level object and its top-level member objects in masked text. */
  private static final class Scanner {
    private final String content;
    private final Map<String, JsonSpan> objects = new HashMap<>();
    private int position;
    private int depth;
    private int rootOpen = -1;
    private int rootClose = -1;
    private Optional<String> openKey = Optional.empty();
    private int openAt;
    private Optional<String> pendingKey = Optional.empty();
    private Optional<String> indent = Optional.empty();

    Scanner(final String content) {
      this.content = content;
    }

    void scan() {
      while (position < content.length() && rootClose < 0) {
        final char next = content.charAt(position);
        if (next == JsonText.QUOTE) {
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
      } else if (!Character.isWhitespace(next)
          && next != JsonText.COMMA
          && next != JsonText.COLON) {
        pendingKey = Optional.empty();
      }
    }

    private void newline() {
      if (depth == 1 && indent.isEmpty()) {
        indent = leadingWhitespace(position + 1);
      }
    }

    private void open(final char next) {
      if (depth == 0 && next == OPEN_OBJECT && rootOpen < 0) {
        rootOpen = position;
      }
      if (depth == 1 && next == OPEN_OBJECT && pendingKey.isPresent()) {
        openKey = pendingKey;
        openAt = position;
      }
      pendingKey = Optional.empty();
      depth++;
    }

    private void close(final char next) {
      depth--;
      if (next == CLOSE_OBJECT && depth == 1 && openKey.isPresent()) {
        objects.putIfAbsent(openKey.get(), new JsonSpan(openAt, position));
        openKey = Optional.empty();
      }
      if (next == CLOSE_OBJECT && depth == 0) {
        rootClose = position;
      }
    }

    private void string() {
      final int start = position + 1;
      position = start;
      while (position < content.length() && content.charAt(position) != JsonText.QUOTE) {
        position += content.charAt(position) == '\\' ? 2 : 1;
      }
      final String value = content.substring(start, Math.min(position, content.length()));
      position++;
      pendingKey = depth == 1 && followedByColon() ? Optional.of(value) : Optional.empty();
    }

    private boolean followedByColon() {
      final int index = JsonText.skipWhitespace(content, position);
      return index < content.length() && content.charAt(index) == JsonText.COLON;
    }

    private Optional<String> leadingWhitespace(final int from) {
      int end = from;
      while (end < content.length()
          && (content.charAt(end) == ' ' || content.charAt(end) == '\t')) {
        end++;
      }
      return end > from && end < content.length() && content.charAt(end) == JsonText.QUOTE
          ? Optional.of(content.substring(from, end))
          : Optional.empty();
    }
  }
}
