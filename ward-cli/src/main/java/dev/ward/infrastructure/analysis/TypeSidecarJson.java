package dev.ward.infrastructure.analysis;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.SpanFacts;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The JSON lines of the type sidecar protocol: a request carries a file's text and span offsets,
 * and a response maps {@code "start:end"} to the facts found for it. Only the shapes the protocol
 * uses are read, so the reader stays small and needs no library.
 */
final class TypeSidecarJson {
  private static final int FIRST_PRINTABLE = 0x20;
  private static final int REQUEST_CAPACITY = 256;
  private static final int HEX_RADIX = 16;
  private static final int HEX_WIDTH = 4;

  private TypeSidecarJson() {}

  /** Encodes one request line for {@code spans} of the file at {@code path} with {@code text}. */
  static String request(
      final String path, final String text, final List<Span> spans, final Set<FactKind> kinds) {
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
    final String names =
        kinds.stream()
            .map(kind -> quote(kind.wireName()))
            .sorted()
            .collect(Collectors.joining(","));
    return line.append("],\"kinds\":[").append(names).append("]}").toString();
  }

  /** Returns the key a span carries on the wire, {@code "start:end"} by character offset. */
  static String key(final Span span) {
    return span.startOffset() + ":" + span.endOffset();
  }

  /**
   * Reads the {@code facts} object of a response line, keyed as the sidecar keyed it.
   *
   * @throws IllegalArgumentException if the line is not a response with well-formed facts
   */
  static Map<String, SpanFacts> facts(final String line) {
    final Object root = new TypeSidecarJsonReader(line).read();
    if (!(root instanceof Map<?, ?> object) || !(object.get("facts") instanceof Map<?, ?> facts)) {
      throw new IllegalArgumentException("not a type response");
    }
    return TypeFactsJson.spanFacts(facts);
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
