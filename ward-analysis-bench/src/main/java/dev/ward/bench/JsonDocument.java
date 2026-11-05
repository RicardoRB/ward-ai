package dev.ward.bench;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the harness's result documents as indented JSON. Maps keep their order, a null value is
 * {@code null}, and a non-finite number is {@code null}, so a missing ratio is not an error.
 */
final class JsonDocument {

  private static final String INDENT = "  ";
  private static final char SPACE = ' ';

  private JsonDocument() {}

  /**
   * Renders a map of maps, lists, strings, numbers and nulls as one JSON document.
   *
   * @param root the document
   * @return the JSON text, ending with a newline
   */
  static String toJson(final Map<String, Object> root) {
    final StringBuilder json = new StringBuilder();
    write(json, root, 0);
    return json.append('\n').toString();
  }

  private static void write(final StringBuilder json, final Object value, final int depth) {
    if (value instanceof Map<?, ?> map) {
      writeContainer(
          json,
          map.entrySet().stream()
              .map(
                  e ->
                      (Map.Entry<String, Object>)
                          new AbstractMap.SimpleImmutableEntry<>(
                              e.getKey().toString(), e.getValue()))
              .toList(),
          depth,
          '{',
          '}');
    } else if (value instanceof List<?> list) {
      writeList(json, list, depth);
    } else {
      writeScalar(json, value);
    }
  }

  private static void writeContainer(
      final StringBuilder json,
      final List<Map.Entry<String, Object>> entries,
      final int depth,
      final char open,
      final char close) {
    json.append(open);
    String separator = "";
    for (final Map.Entry<String, Object> entry : entries) {
      json.append(separator).append('\n').append(INDENT.repeat(depth + 1));
      writeString(json, entry.getKey());
      json.append(": ");
      write(json, entry.getValue(), depth + 1);
      separator = ",";
    }
    if (!entries.isEmpty()) {
      json.append('\n').append(INDENT.repeat(depth));
    }
    json.append(close);
  }

  private static void writeList(final StringBuilder json, final List<?> list, final int depth) {
    json.append('[');
    String separator = "";
    for (final Object item : list) {
      json.append(separator).append('\n').append(INDENT.repeat(depth + 1));
      write(json, item, depth + 1);
      separator = ",";
    }
    if (!list.isEmpty()) {
      json.append('\n').append(INDENT.repeat(depth));
    }
    json.append(']');
  }

  private static void writeScalar(final StringBuilder json, final Object value) {
    if (value == null) {
      json.append("null");
    } else if (value instanceof String text) {
      writeString(json, text);
    } else if (value instanceof Double number) {
      json.append(number.isNaN() || number.isInfinite() ? "null" : number.toString());
    } else {
      json.append(value);
    }
  }

  private static void writeString(final StringBuilder json, final String text) {
    json.append('"');
    for (final char c : text.toCharArray()) {
      switch (c) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '\n' -> json.append("\\n");
        case '\r' -> json.append("\\r");
        case '\t' -> json.append("\\t");
        default -> {
          if (c < SPACE) {
            json.append(String.format("\\u%04x", (int) c));
          } else {
            json.append(c);
          }
        }
      }
    }
    json.append('"');
  }
}
