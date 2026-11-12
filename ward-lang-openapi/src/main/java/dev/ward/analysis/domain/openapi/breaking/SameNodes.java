package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.List;

/** Compares two nodes by what they say, not by where they are written. */
final class SameNodes {

  private SameNodes() {}

  /** Tells whether both nodes have the same kind, text, entries and items. */
  static boolean equal(final DocNode first, final DocNode second) {
    return first.kind() == second.kind()
        && first.text().equals(second.text())
        && sameEntries(first, second)
        && sameItems(first.items(), second.items());
  }

  private static boolean sameEntries(final DocNode first, final DocNode second) {
    if (first.entries().size() != second.entries().size()) {
      return false;
    }
    for (final DocNode.Entry entry : first.entries()) {
      if (second.get(entry.key()).filter(value -> equal(entry.value(), value)).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameItems(final List<DocNode> first, final List<DocNode> second) {
    if (first.size() != second.size()) {
      return false;
    }
    for (int index = 0; index < first.size(); index++) {
      if (!equal(first.get(index), second.get(index))) {
        return false;
      }
    }
    return true;
  }
}
