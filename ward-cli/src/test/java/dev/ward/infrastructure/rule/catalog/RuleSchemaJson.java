package dev.ward.infrastructure.rule.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds and writes the JSON of {@link RuleSchema}: objects keep their insertion order, and the
 * text uses two-space indentation, so the committed file diffs cleanly.
 */
final class RuleSchemaJson {

  private static final String INDENT = "  ";

  private RuleSchemaJson() {}

  /** Returns a mutable object whose keys keep their insertion order. */
  static Map<String, Object> obj(final Object... keysAndValues) {
    final Map<String, Object> map = new LinkedHashMap<>();
    for (int index = 0; index < keysAndValues.length; index += 2) {
      map.put((String) keysAndValues[index], keysAndValues[index + 1]);
    }
    return map;
  }

  /** Returns an array schema whose items follow {@code items}. */
  static Map<String, Object> array(final Object items) {
    return obj("type", "array", "items", items);
  }

  /** Appends {@code value} as JSON, nested at {@code depth} indentation levels. */
  static void write(final Object value, final int depth, final StringBuilder out) {
    if (value instanceof Map<?, ?> map) {
      writeMap(map, depth, out);
    } else if (value instanceof List<?> list) {
      writeList(list, depth, out);
    } else if (value instanceof String text) {
      out.append(quote(text));
    } else {
      out.append(value);
    }
  }

  private static void writeMap(final Map<?, ?> map, final int depth, final StringBuilder out) {
    if (map.isEmpty()) {
      out.append("{}");
      return;
    }
    out.append('{');
    boolean first = true;
    for (final Map.Entry<?, ?> entry : map.entrySet()) {
      out.append(first ? "\n" : ",\n")
          .append(INDENT.repeat(depth + 1))
          .append(quote(String.valueOf(entry.getKey())))
          .append(": ");
      write(entry.getValue(), depth + 1, out);
      first = false;
    }
    out.append('\n').append(INDENT.repeat(depth)).append('}');
  }

  private static void writeList(final List<?> list, final int depth, final StringBuilder out) {
    if (list.isEmpty()) {
      out.append("[]");
      return;
    }
    out.append('[');
    boolean first = true;
    for (final Object item : list) {
      out.append(first ? "\n" : ",\n").append(INDENT.repeat(depth + 1));
      write(item, depth + 1, out);
      first = false;
    }
    out.append('\n').append(INDENT.repeat(depth)).append(']');
  }

  private static String quote(final String text) {
    final StringBuilder quoted = new StringBuilder("\"");
    for (final char character : text.toCharArray()) {
      quoted.append(escape(character));
    }
    return quoted.append('"').toString();
  }

  private static String escape(final char character) {
    return switch (character) {
      case '"' -> "\\\"";
      case '\\' -> "\\\\";
      case '\n' -> "\\n";
      case '\t' -> "\\t";
      default ->
          character < ' '
              ? String.format(Locale.ROOT, "\\u%04x", (int) character)
              : String.valueOf(character);
    };
  }
}
