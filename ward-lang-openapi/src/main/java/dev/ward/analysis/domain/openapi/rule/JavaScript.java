package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.Locale;
import java.util.regex.Pattern;

/** The few JavaScript conversions the Spectral rules depend on. */
final class JavaScript {

  private static final Pattern DECIMAL =
      Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

  private JavaScript() {}

  /** {@code Number(text)}: an empty text is 0 and a text that is not a number is NaN. */
  static double number(final String text) {
    final String trimmed = text.strip();
    if (trimmed.isEmpty()) {
      return 0;
    }
    return DECIMAL.matcher(trimmed).matches() ? Double.parseDouble(trimmed) : Double.NaN;
  }

  /** The string template of a member: {@code undefined} when it is missing, else its text. */
  static String text(final DocNode object, final String key) {
    return object.get(key).map(DocNode::text).orElse("undefined");
  }

  /**
   * The name Spectral gives the type of a value: {@code integer} only when {@code integers} says to
   * look for it.
   */
  static String typeOf(final DocNode value, final boolean integers) {
    return switch (value.kind()) {
      case STRING -> "string";
      case BOOLEAN -> "boolean";
      case NULL -> "null";
      case ARRAY -> "array";
      case OBJECT -> "object";
      case NUMBER -> integers && isInteger(value.text()) ? "integer" : "number";
    };
  }

  private static boolean isInteger(final String text) {
    final double number = number(text);
    return !Double.isNaN(number) && !Double.isInfinite(number) && number == Math.rint(number);
  }

  /** {@code printValue} of Spectral: a scalar as JSON, an array as {@code Array[]}. */
  static String print(final DocNode value) {
    return switch (value.kind()) {
      case STRING -> "\"" + value.text().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
      case NUMBER ->
          isInteger(value.text())
              ? String.format(Locale.ROOT, "%.0f", number(value.text()))
              : Double.toString(number(value.text()));
      case BOOLEAN, NULL -> value.kind() == DocNode.Kind.NULL ? "null" : value.text();
      case ARRAY -> "Array[]";
      case OBJECT -> "Object{}";
    };
  }

  /** {@code JSON.stringify} of a string. */
  static String print(final String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
