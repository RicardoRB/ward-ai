package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.stream.Collectors;

/** {@code JSON.stringify} of a node, which two requirements are compared by. */
final class JsonText {

  private JsonText() {}

  /** Writes the node without spaces, keys in file order. */
  static String write(final DocNode node) {
    return switch (node.kind()) {
      case OBJECT ->
          node.entries().stream()
              .map(entry -> JavaScript.print(entry.key()) + ":" + write(entry.value()))
              .collect(Collectors.joining(",", "{", "}"));
      case ARRAY ->
          node.items().stream().map(JsonText::write).collect(Collectors.joining(",", "[", "]"));
      default -> JavaScript.print(node);
    };
  }
}
