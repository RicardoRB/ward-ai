package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The named parts of an operation: its parameters, responses and headers. */
final class NamedParts {

  private NamedParts() {}

  /** The parameters of an operation by {@code in:name}; an operation's win over its path's. */
  static Map<String, DocNode> parameters(
      final OpenApiDocument document, final Operation operation) {
    final Map<String, DocNode> parameters = new LinkedHashMap<>();
    for (final DocNode owner : List.of(operation.pathItem(), operation.node())) {
      for (final DocNode parameter :
          owner.get("parameters").map(DocNode::items).orElse(List.of())) {
        final DocNode resolved = document.resolve(parameter);
        parameters.put(
            resolved.string("in").orElse("") + ":" + resolved.string("name").orElse(""), resolved);
      }
    }
    return parameters;
  }

  static List<DocNode.Entry> entries(
      final OpenApiDocument document, final DocNode owner, final String key) {
    return owner.get(key).map(document::resolve).map(DocNode::entries).orElse(List.of());
  }

  static Optional<DocNode> entry(
      final OpenApiDocument document, final DocNode owner, final String key, final String name) {
    return entries(document, owner, key).stream()
        .filter(entry -> entry.key().equals(name))
        .map(DocNode.Entry::value)
        .findFirst();
  }
}
