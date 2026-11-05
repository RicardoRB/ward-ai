package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.ir.Span;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON lines of the type sidecar protocol: a request carries a file's text and span offsets,
 * and a response maps {@code "start:end"} to a type name. Only the shapes the protocol uses are
 * read, so the reader stays small and needs no library.
 */
final class TypeSidecarJson {
  private static final int FIRST_PRINTABLE = 0x20;
  private static final int REQUEST_CAPACITY = 256;
  private static final int HEX_RADIX = 16;
  private static final int HEX_WIDTH = 4;

  private TypeSidecarJson() {}

  /** Encodes one request line for {@code spans} of the file at {@code path} with {@code text}. */
  static String request(final String path, final String text, final List<Span> spans) {
    final StringBuilder line =
        new StringBuilder(REQUEST_CAPACITY)
            .append("{\"path\":")
            .append(quote(path))
            .append(",\"text\":")
            .append(quote(text))
            .append(",\"spans\":[");
    for (int index = 0; index < spans.size(); index++) {
      final Span span = spans.get(index);
      line.append(index == 0 ? "" : ",")
          .append('[')
          .append(span.startOffset())
          .append(',')
          .append(span.endOffset())
          .append(']');
    }
    return line.append("]}").toString();
  }

  /** Returns the key a span carries on the wire, {@code "start:end"} by character offset. */
  static String key(final Span span) {
    return span.startOffset() + ":" + span.endOffset();
  }

  /**
   * Reads the {@code types} object of a response line.
   *
   * @throws IllegalArgumentException if the line is not a response with string type names
   */
  static Map<String, String> types(final String line) {
    final Object root = new TypeSidecarJsonReader(line).read();
    if (!(root instanceof Map<?, ?> object) || !(object.get("types") instanceof Map<?, ?> types)) {
      throw new IllegalArgumentException("not a type response");
    }
    final Map<String, String> result = new LinkedHashMap<>();
    types.forEach(
        (key, value) -> {
          if (!(key instanceof String name) || !(value instanceof String type)) {
            throw new IllegalArgumentException("type entries must be strings");
          }
          result.put(name, type);
        });
    return result;
  }

  /** Encodes {@code value} as a JSON string literal. */
  static String quote(final String value) {
    final StringBuilder quoted = new StringBuilder("\"");
    for (final char character : value.toCharArray()) {
      quoted.append(escape(character));
    }
    return quoted.append('"').toString();
  }

  private static String escape(final char character) {
    return switch (character) {
      case '"' -> "\\\"";
      case '\\' -> "\\\\";
      case '\n' -> "\\n";
      case '\r' -> "\\r";
      case '\t' -> "\\t";
      default -> character < FIRST_PRINTABLE ? unicode(character) : String.valueOf(character);
    };
  }

  private static String unicode(final char character) {
    final String hex = Integer.toString(character, HEX_RADIX);
    return "\\u" + "0".repeat(HEX_WIDTH - hex.length()) + hex;
  }
}
