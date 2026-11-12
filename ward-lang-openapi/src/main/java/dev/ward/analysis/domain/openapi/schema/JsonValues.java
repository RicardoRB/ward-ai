package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.List;
import java.util.Optional;

/** How JSON Schema compares and measures the values of a document. */
final class JsonValues {

  private static final String TRUE = "true";

  private JsonValues() {}

  /** Deep equality: numbers by value, objects regardless of key order. */
  static boolean equal(final DocNode left, final DocNode right) {
    if (left.kind() != right.kind()) {
      return false;
    }
    return switch (left.kind()) {
      case NUMBER -> number(left) == number(right);
      case ARRAY -> equalItems(left.items(), right.items());
      case OBJECT -> equalEntries(left, right);
      default -> left.text().equals(right.text());
    };
  }

  private static boolean equalItems(final List<DocNode> left, final List<DocNode> right) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int index = 0; index < left.size(); index++) {
      if (!equal(left.get(index), right.get(index))) {
        return false;
      }
    }
    return true;
  }

  private static boolean equalEntries(final DocNode left, final DocNode right) {
    if (left.entries().size() != right.entries().size()) {
      return false;
    }
    for (final DocNode.Entry entry : left.entries()) {
      final Optional<DocNode> other = right.get(entry.key());
      if (other.isEmpty() || !equal(entry.value(), other.get())) {
        return false;
      }
    }
    return true;
  }

  /** The numeric value of a number node. */
  static double number(final DocNode node) {
    final String text = node.text().replace("_", "");
    try {
      return Double.parseDouble(text);
    } catch (final NumberFormatException exception) {
      return Double.NaN;
    }
  }

  /** Tells whether a number node holds a whole number. */
  static boolean isInteger(final DocNode node) {
    final double value = number(node);
    return !Double.isNaN(value) && !Double.isInfinite(value) && value == Math.rint(value);
  }

  /** The count of Unicode code points of a string, which is what {@code minLength} counts. */
  static int length(final String text) {
    return text.codePointCount(0, text.length());
  }

  /** A key under which equal scalars collide, numbers by value. */
  static String canonical(final DocNode node, final String fallback) {
    return node.kind() == DocNode.Kind.NUMBER ? "number:" + show(number(node)) : fallback;
  }

  /** JSON pointer segment escaping. */
  static String escape(final String key) {
    return key.replace("~", "~0").replace("/", "~1");
  }

  /** Prints a number as JavaScript does: a whole number without a fraction. */
  static String show(final double value) {
    return value == Math.rint(value) && !Double.isInfinite(value)
        ? Long.toString((long) value)
        : Double.toString(value);
  }

  /** Tells whether the node is present and the boolean {@code true}. */
  static boolean isTrue(final Optional<DocNode> node) {
    return node.filter(flag -> flag.kind() == DocNode.Kind.BOOLEAN && TRUE.equals(flag.text()))
        .isPresent();
  }
}
