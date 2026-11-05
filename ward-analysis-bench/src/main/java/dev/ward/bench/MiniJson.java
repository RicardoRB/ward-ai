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
  private static final String UNICODE = "u";
  private static final Map<Character, Character> ESCAPES =
      Map.of(
          '"', '"',
          '\\', '\\',
          '/', '/',
          'b', '\b',
          'f', '\f',
          'n', '\n',
          'r', '\r',
          't', '\t');

  private final JsonCursor cursor;

  private MiniJson(final String text) {
    this.cursor = new JsonCursor(text);
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
    if (!reader.cursor.atEnd()) {
      throw reader.cursor.error("text after the document");
    }
    return value;
  }

  private Object value() {
    return switch (cursor.peekOr('\0')) {
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
    cursor.expect('{');
    if (cursor.consume('}')) {
      return members;
    }
    do {
      final String key = string();
      cursor.expect(':');
      members.put(key, value());
    } while (cursor.consume(','));
    cursor.expect('}');
    return members;
  }

  private List<Object> array() {
    final List<Object> items = new ArrayList<>();
    cursor.expect('[');
    if (cursor.consume(']')) {
      return items;
    }
    do {
      items.add(value());
    } while (cursor.consume(','));
    cursor.expect(']');
    return items;
  }

  private String string() {
    cursor.expect('"');
    final StringBuilder out = new StringBuilder();
    char c = cursor.next();
    while (c != '"') {
      out.append(c == '\\' ? escape() : c);
      c = cursor.next();
    }
    return out.toString();
  }

  private char escape() {
    final char c = cursor.next();
    if (UNICODE.charAt(0) == c) {
      return unicode();
    }
    final Character simple = ESCAPES.get(c);
    if (simple == null) {
      throw cursor.error("unknown escape");
    }
    return simple;
  }

  private char unicode() {
    final int start = cursor.position();
    for (int digit = 0; digit < HEX_DIGITS; digit++) {
      cursor.next();
    }
    return (char) Integer.parseInt(cursor.slice(start), HEX_RADIX);
  }

  private Object literal(final String word, final Object value) {
    for (final char expected : word.toCharArray()) {
      if (cursor.next() != expected) {
        throw cursor.error("unknown literal");
      }
    }
    return value;
  }

  private Double number() {
    final int start = cursor.position();
    if (cursor.skipWhile(NUMBER_CHARS) == 0) {
      throw cursor.error("expected a value");
    }
    return Double.valueOf(cursor.slice(start));
  }
}
