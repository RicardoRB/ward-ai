package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.Optional;

/** Reads a value of a document by JSON pointer. */
public final class Pointers {

  private Pointers() {}

  /** The node at the pointer below {@code root}, or empty when a segment does not exist. */
  public static Optional<DocNode> at(final DocNode root, final String pointer) {
    DocNode current = root;
    if (pointer.isEmpty()) {
      return Optional.of(root);
    }
    for (final String raw : pointer.substring(1).split("/", -1)) {
      final Optional<DocNode> next = child(current, raw.replace("~1", "/").replace("~0", "~"));
      if (next.isEmpty()) {
        return Optional.empty();
      }
      current = next.get();
    }
    return Optional.of(current);
  }

  /** The child of {@code node} named {@code key}, or at the index {@code key} of an array. */
  public static Optional<DocNode> child(final DocNode node, final String key) {
    return node.kind() == DocNode.Kind.ARRAY && key.chars().allMatch(Character::isDigit)
        ? node.at(Integer.parseInt(key))
        : node.get(key);
  }
}
