package dev.ward.infrastructure.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Typed reads of the values {@link TypeSidecarJsonReader} returns; a wrong shape is rejected. */
final class JsonValues {
  private JsonValues() {}

  /** A Long when the digits have no fraction or exponent, else a Double. */
  static Object parseNumber(final String digits) {
    try {
      return digits.matches("-?\\d+") ? (Object) Long.valueOf(digits) : Double.valueOf(digits);
    } catch (NumberFormatException malformed) {
      throw new IllegalArgumentException("bad number", malformed);
    }
  }

  static Map<?, ?> object(final Object value) {
    if (value instanceof Map<?, ?> map) {
      return map;
    }
    throw new IllegalArgumentException("expected an object");
  }

  static List<?> list(final Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      return list;
    }
    throw new IllegalArgumentException("expected an array");
  }

  static List<String> strings(final Object value) {
    final List<String> strings = new ArrayList<>();
    list(value).forEach(item -> strings.add(string(item)));
    return strings;
  }

  static String string(final Object value) {
    if (value instanceof String text) {
      return text;
    }
    throw new IllegalArgumentException("expected a string");
  }

  static long number(final Object value) {
    if (value instanceof Long number) {
      return number;
    }
    throw new IllegalArgumentException("expected an integer");
  }

  static boolean flag(final Object value) {
    return value != null && (value instanceof Boolean flag ? flag : unexpectedFlag());
  }

  static boolean unexpectedFlag() {
    throw new IllegalArgumentException("expected a boolean");
  }
}
