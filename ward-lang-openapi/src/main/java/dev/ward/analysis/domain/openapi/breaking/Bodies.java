package dev.ward.analysis.domain.openapi.breaking;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import java.util.List;
import java.util.Optional;

/** Reads the request body of an operation and its media types as the resolved document shows. */
final class Bodies {

  private Bodies() {}

  /** The request body of {@code operation}, with its references followed. */
  static Optional<DocNode> request(final OpenApiDocument document, final Operation operation) {
    return operation.node().get("requestBody").map(document::resolve);
  }

  /** Tells whether the body or parameter node says {@code required: true}. */
  static boolean required(final DocNode node) {
    return node.get("required").filter(value -> "true".equals(value.text())).isPresent();
  }

  /** The media types of a body: the entries of its {@code content}, with references followed. */
  static List<DocNode.Entry> mediaTypes(final OpenApiDocument document, final DocNode body) {
    return body.get("content").map(document::resolve).map(DocNode::entries).orElse(List.of());
  }

  /** The media type entry named {@code name}, if any. */
  static Optional<DocNode> mediaType(
      final OpenApiDocument document, final DocNode body, final String name) {
    return mediaTypes(document, body).stream()
        .filter(entry -> entry.key().equals(name))
        .map(entry -> document.resolve(entry.value()))
        .findFirst();
  }

  /** The schema of a media type, with references followed, if any. */
  static Optional<DocNode> schema(final OpenApiDocument document, final DocNode mediaType) {
    return mediaType.get("schema").map(document::resolve);
  }
}
