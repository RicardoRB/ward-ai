package dev.ward.infrastructure.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A recursive-descent reader for the JSON shapes the type sidecar protocol uses. */
/** A recursive-descent reader for objects, arrays, strings, numbers and literals. */
final class TypeSidecarJsonReader {
  private static final int HEX_RADIX = 16;
  private static final int HEX_WIDTH = 4;

  private final String text;
  private int position;

  TypeSidecarJsonReader(final String text) {
    this.text = text;
  }

  Object read() {
    final Object value = value();
    skipSpace();
    if (position != text.length()) {
      throw new IllegalArgumentException("trailing characters");
    }
    return value;
  }

  private Object value() {
    skipSpace();
    if (position >= text.length()) {
      throw new IllegalArgumentException("unexpected end");
    }
    final char next = text.charAt(position);
    return switch (next) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      default -> literal();
    };
  }

  private Map<String, Object> object() {
    final Map<String, Object> result = new LinkedHashMap<>();
    expect('{');
    skipSpace();
    if (accept('}')) {
      return result;
    }
    do {
      skipSpace();
      final String key = string();
      skipSpace();
      expect(':');
      result.put(key, value());
      skipSpace();
    } while (accept(','));
    expect('}');
    return result;
  }

  private List<Object> array() {
    final List<Object> result = new ArrayList<>();
    expect('[');
    skipSpace();
    if (accept(']')) {
      return result;
    }
    do {
      result.add(value());
      skipSpace();
    } while (accept(','));
    expect(']');
    return result;
  }

  private String string() {
    expect('"');
    final StringBuilder value = new StringBuilder();
    while (position < text.length() && text.charAt(position) != '"') {
      final char next = text.charAt(position);
      position++;
      value.append(next == '\\' ? escaped() : next);
    }
    expect('"');
    return value.toString();
  }

  private char escaped() {
    final char code = text.charAt(position);
    position++;
    return switch (code) {
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case 'b' -> '\b';
      case 'f' -> '\f';
      case 'u' -> {
        final char value =
            (char) Integer.parseInt(text.substring(position, position + HEX_WIDTH), HEX_RADIX);
        position += HEX_WIDTH;
        yield value;
      }
      default -> code;
    };
  }

  private Object literal() {
    for (final String word : List.of("true", "false", "null")) {
      if (text.startsWith(word, position)) {
        position += word.length();
        return "null".equals(word) ? null : Boolean.valueOf(word);
      }
    }
    throw new IllegalArgumentException("unsupported value");
  }

  private void skipSpace() {
    while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
      position++;
    }
  }

  private boolean accept(final char expected) {
    if (position < text.length() && text.charAt(position) == expected) {
      position++;
      return true;
    }
    return false;
  }

  private void expect(final char expected) {
    if (!accept(expected)) {
      throw new IllegalArgumentException("expected " + expected);
    }
  }
}
