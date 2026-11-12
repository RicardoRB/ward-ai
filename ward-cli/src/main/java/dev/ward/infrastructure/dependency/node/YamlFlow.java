package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.LockfileFormatException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A flow collection on one line: {@code {a: b, c: d}} or {@code [x, y]}. */
final class YamlFlow {
  private static final char OPEN_MAP = '{';
  private static final char CLOSE_MAP = '}';
  private static final char OPEN_LIST = '[';
  private static final char CLOSE_LIST = ']';
  private static final char COMMA = ',';
  private static final char COLON = ':';
  private static final char SPACE = ' ';
  private static final char NONE = '\0';

  private final String text;
  private final int line;
  private int at;

  /** Creates a reader of {@code text}, which sits on the zero-based line {@code line}. */
  YamlFlow(final String text, final int line) {
    this.text = text;
    this.line = line;
  }

  /**
   * Parses the whole text.
   *
   * @throws LockfileFormatException with the line number if the text is not a flow collection
   */
  Object parse() {
    final Object value = element();
    skipSpaces();
    if (at != text.length()) {
      throw fail();
    }
    return value;
  }

  private Object element() {
    skipSpaces();
    if (at >= text.length()) {
      throw fail();
    }
    return switch (text.charAt(at)) {
      case OPEN_MAP -> map();
      case OPEN_LIST -> list();
      default -> YamlSubset.unquote(token(false));
    };
  }

  private Map<String, Object> map() {
    final Map<String, Object> map = new LinkedHashMap<>();
    at++;
    skipSpaces();
    if (closes(CLOSE_MAP)) {
      return map;
    }
    boolean more = true;
    while (more) {
      final String key = YamlSubset.unquote(token(true));
      skipSpaces();
      expect(COLON);
      map.put(key, element());
      more = separator(CLOSE_MAP);
    }
    return map;
  }

  private List<Object> list() {
    final List<Object> list = new ArrayList<>();
    at++;
    skipSpaces();
    if (closes(CLOSE_LIST)) {
      return list;
    }
    boolean more = true;
    while (more) {
      list.add(element());
      more = separator(CLOSE_LIST);
    }
    return list;
  }

  /** Consumes {@code close} when it is next and returns whether it was. */
  private boolean closes(final char close) {
    if (peek() == close) {
      at++;
      return true;
    }
    return false;
  }

  /** Consumes a comma (more follows) or {@code close} (the collection ends). */
  private boolean separator(final char close) {
    skipSpaces();
    final char next = peek();
    at++;
    if (next != close && next != COMMA) {
      throw fail();
    }
    return next == COMMA;
  }

  private void expect(final char expected) {
    if (peek() != expected) {
      throw fail();
    }
    at++;
  }

  private String token(final boolean key) {
    skipSpaces();
    final int start = at;
    final char first = peek();
    if (first == '\'' || first == '"') {
      final int close = YamlSubset.closingQuote(text, at, first);
      if (close < 0) {
        throw fail();
      }
      at = close + 1;
      return text.substring(start, at);
    }
    while (at < text.length() && !endsToken(text.charAt(at), key)) {
      at++;
    }
    return text.substring(start, at).strip();
  }

  private static boolean endsToken(final char current, final boolean key) {
    return current == COMMA
        || current == CLOSE_MAP
        || current == CLOSE_LIST
        || key && current == COLON;
  }

  private char peek() {
    return at < text.length() ? text.charAt(at) : NONE;
  }

  private void skipSpaces() {
    while (at < text.length() && text.charAt(at) == SPACE) {
      at++;
    }
  }

  private LockfileFormatException fail() {
    return new LockfileFormatException(
        "line " + (line + 1) + " has a shape the reader does not know");
  }
}
