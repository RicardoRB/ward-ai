package dev.ward.bench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads one JSON document into maps, lists, strings, numbers, booleans and nulls. The harness reads
 * only the output of {@code ward check --format json}, so it needs no JSON library.
 */
final class MiniJson {

  private static final int HEX_DIGITS = 4;
  private static final int HEX_RADIX = 16;
  private static final String NUMBER_CHARS = "+-.eE0123456789";

  private final String text;
  private int position;

  private MiniJson(final String text) {
    this.text = text;
  }

  /**
   * Parses the document.
   *
   * @param text the JSON text
   * @return the value: a map, list, string, number, boolean or null
   * @throws IllegalArgumentException if the text is not one JSON value
   */
  static Object parse(final String text) {
    final MiniJson reader = new MiniJson(text);
    final Object value = reader.value();
    reader.skipSpace();
    if (reader.position != text.length()) {
      throw reader.error("text after the document");
    }
    return value;
  }

  private Object value() {
    skipSpace();
    return switch (peek()) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      case 't' -> literal("true", Boolean.TRUE);
      case 'f' -> literal("false", Boolean.FALSE);
      case 'n' -> literal("null", null);
      default -> number();
    };
  }

  private Map<String, Object> object() {
    final Map<String, Object> members = new LinkedHashMap<>();
    expect('{');
    skipSpace();
    if (peekIs('}')) {
      position++;
      return members;
    }
    do {
      skipSpace();
      final String key = string();
      skipSpace();
      expect(':');
      members.put(key, value());
      skipSpace();
    } while (consume(','));
    expect('}');
    return members;
  }

  private List<Object> array() {
    final List<Object> items = new ArrayList<>();
    expect('[');
    skipSpace();
    if (peekIs(']')) {
      position++;
      return items;
    }
    do {
      items.add(value());
      skipSpace();
    } while (consume(','));
    expect(']');
    return items;
  }

  private String string() {
    expect('"');
    final StringBuilder out = new StringBuilder();
    while (position < text.length()) {
      final char c = text.charAt(position++);
      if (c == '"') {
        return out.toString();
      }
      out.append(c == '\\' ? escape() : c);
    }
    throw error("unterminated string");
  }

  private char escape() {
    final char c = next();
    return switch (c) {
      case '"', '\\', '/' -> c;
      case 'b' -> '\b';
      case 'f' -> '\f';
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case 'u' -> unicode();
      default -> throw error("unknown escape");
    };
  }

  private char unicode() {
    if (position + HEX_DIGITS > text.length()) {
      throw error("short unicode escape");
    }
    final int code = Integer.parseInt(text.substring(position, position + HEX_DIGITS), HEX_RADIX);
    position += HEX_DIGITS;
    return (char) code;
  }

  private Object literal(final String word, final Object value) {
    if (!text.startsWith(word, position)) {
      throw error("unknown literal");
    }
    position += word.length();
    return value;
  }

  private Double number() {
    final int start = position;
    while (position < text.length() && NUMBER_CHARS.indexOf(text.charAt(position)) >= 0) {
      position++;
    }
    if (start == position) {
      throw error("expected a value");
    }
    return Double.valueOf(text.substring(start, position));
  }

  private void skipSpace() {
    while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
      position++;
    }
  }

  private char peek() {
    if (position >= text.length()) {
      throw error("unexpected end of document");
    }
    return text.charAt(position);
  }

  private boolean peekIs(final char expected) {
    return position < text.length() && text.charAt(position) == expected;
  }

  private boolean consume(final char expected) {
    skipSpace();
    if (peekIs(expected)) {
      position++;
      return true;
    }
    return false;
  }

  private void expect(final char expected) {
    if (!consume(expected)) {
      throw error("expected '" + expected + "'");
    }
  }

  private char next() {
    final char c = peek();
    position++;
    return c;
  }

  private IllegalArgumentException error(final String problem) {
    return new IllegalArgumentException("Invalid JSON at offset " + position + ": " + problem);
  }
}
