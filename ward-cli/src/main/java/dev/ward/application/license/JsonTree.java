package dev.ward.application.license;

import java.util.List;
import java.util.Map;

/**
 * Writes a tree of maps, lists, strings and numbers as indented JSON, in the order of the maps.
 * Hand-written so that the SBOM bytes depend on nothing but the data, and so that keys such as
 * {@code bom-ref} need no annotation.
 */
final class JsonTree {
  private static final String INDENT = "  ";
  private static final int CONTROL_LIMIT = 0x20;

  private JsonTree() {}

  /** Returns {@code value} as JSON text, ending in a newline. */
  static String write(final Object value) {
    final StringBuilder out = new StringBuilder();
    append(out, value, 0);
    return out.append('\n').toString();
  }

  private static void append(final StringBuilder out, final Object value, final int depth) {
    if (value instanceof Map<?, ?> map) {
      object(out, map, depth);
    } else if (value instanceof List<?> list) {
      array(out, list, depth);
    } else if (value instanceof Number) {
      out.append(value);
    } else {
      quote(out, String.valueOf(value));
    }
  }

  private static void object(final StringBuilder out, final Map<?, ?> map, final int depth) {
    if (map.isEmpty()) {
      out.append("{}");
      return;
    }
    out.append("{\n");
    boolean first = true;
    for (final Map.Entry<?, ?> entry : map.entrySet()) {
      if (!first) {
        out.append(",\n");
      }
      first = false;
      out.append(INDENT.repeat(depth + 1));
      quote(out, String.valueOf(entry.getKey()));
      out.append(": ");
      append(out, entry.getValue(), depth + 1);
    }
    out.append('\n').append(INDENT.repeat(depth)).append('}');
  }

  private static void array(final StringBuilder out, final List<?> list, final int depth) {
    if (list.isEmpty()) {
      out.append("[]");
      return;
    }
    out.append("[\n");
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        out.append(",\n");
      }
      out.append(INDENT.repeat(depth + 1));
      append(out, list.get(i), depth + 1);
    }
    out.append('\n').append(INDENT.repeat(depth)).append(']');
  }

  private static void quote(final StringBuilder out, final String text) {
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      final char character = text.charAt(i);
      switch (character) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (character < CONTROL_LIMIT) {
            out.append(String.format("\\u%04x", (int) character));
          } else {
            out.append(character);
          }
        }
      }
    }
    out.append('"');
  }
}
