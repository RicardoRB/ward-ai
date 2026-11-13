package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.List;

/** Compares nodes by what they hold, ignoring where they are written. */
final class DocEquality {

  private DocEquality() {}

  /** Tells whether two nodes hold the same value: same kind, text, entries and items. */
  static boolean same(final DocNode left, final DocNode right) {
    if (left.kind() != right.kind() || !left.text().equals(right.text())) {
      return false;
    }
    return sameEntries(left, right) && sameItems(left.items(), right.items());
  }

  /** The JSON type of a scalar or collection, as a schema validator names it. */
  static String typeName(final DocNode node) {
    return switch (node.kind()) {
      case OBJECT -> "object";
      case ARRAY -> "array";
      case STRING -> "string";
      case BOOLEAN -> "boolean";
      case NULL -> "null";
      case NUMBER -> node.text().matches("[-+]?[0-9]+") ? "integer" : "number";
    };
  }

  private static boolean sameEntries(final DocNode left, final DocNode right) {
    if (left.entries().size() != right.entries().size()) {
      return false;
    }
    for (final DocNode.Entry entry : left.entries()) {
      final boolean found =
          right.get(entry.key()).map(other -> same(entry.value(), other)).orElse(false);
      if (!found) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameItems(final List<DocNode> left, final List<DocNode> right) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int index = 0; index < left.size(); index++) {
      if (!same(left.get(index), right.get(index))) {
        return false;
      }
    }
    return true;
  }
}
