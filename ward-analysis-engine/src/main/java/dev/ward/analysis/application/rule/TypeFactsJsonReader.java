package dev.ward.analysis.application.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A recursive-descent reader for objects, arrays, strings, numbers and literals. */
final class TypeFactsJsonReader {
  private static final char UNICODE_ESCAPE = 'u';
  private static final String ESCAPE_KEYS = "nrtbf";
  private static final String ESCAPE_VALUES = "\n\r\t\b\f";
  private static final Pattern NUMBER = Pattern.compile("[-0-9][0-9.eE+-]*");
  private static final List<String> KEYWORDS = List.of("true", "false", "null");
  private static final int HEX_RADIX = 16;
  private static final int HEX_WIDTH = 4;

  private final String text;
  private int position;

  TypeFactsJsonReader(final String text) {
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
    if (code == UNICODE_ESCAPE) {
      final char value =
          (char) Integer.parseInt(text.substring(position, position + HEX_WIDTH), HEX_RADIX);
      position += HEX_WIDTH;
      return value;
    }
    final int index = ESCAPE_KEYS.indexOf(code);
    return index < 0 ? code : ESCAPE_VALUES.charAt(index);
  }

  private Object literal() {
    final Matcher number = NUMBER.matcher(text).region(position, text.length());
    if (number.lookingAt()) {
      position = number.end();
      return JsonValues.parseNumber(number.group());
    }
    final Optional<String> word =
        KEYWORDS.stream().filter(candidate -> text.startsWith(candidate, position)).findFirst();
    if (word.isEmpty()) {
      throw new IllegalArgumentException("unsupported value");
    }
    position += word.get().length();
    return "null".equals(word.get()) ? null : Boolean.valueOf(word.get());
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
