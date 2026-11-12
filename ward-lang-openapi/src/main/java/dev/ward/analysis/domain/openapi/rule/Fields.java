package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiDocument.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** How Spectral reads a field of an object and where it reports a result about it. */
final class Fields {

  private Fields() {}

  /** JavaScript truthiness of a scalar; an object and an array are always truthy. */
  static boolean truthy(final DocNode node) {
    return switch (node.kind()) {
      case STRING -> !node.text().isEmpty();
      case NUMBER -> Double.parseDouble(node.text()) != 0;
      case BOOLEAN -> "true".equals(node.text());
      case NULL -> false;
      default -> true;
    };
  }

  /** The field at {@code path} below {@code base} in the resolved view, if every step exists. */
  static Optional<DocNode> at(
      final OpenApiDocument document, final DocNode base, final String... path) {
    DocNode current = document.resolve(base);
    for (final String key : path) {
      final Optional<DocNode> next = document.get(current, key, View.RESOLVED);
      if (next.isEmpty()) {
        return Optional.empty();
      }
      current = next.get();
    }
    return Optional.of(current);
  }

  /**
   * The finding of Spectral's {@code truthy} on the field at {@code path} of {@code base}: it
   * points at the field when it is falsy and at the deepest part of the path that exists when the
   * field is missing.
   */
  static List<OpenApiFinding> requireTruthy(
      final OpenApiDocument document,
      final DocNode base,
      final String message,
      final String... path) {
    final List<DocNode> chain = new ArrayList<>();
    DocNode current = document.resolve(base);
    if (current.isScalar() && current.kind() != DocNode.Kind.NULL) {
      return truthy(current) ? List.of() : List.of(Findings.atValue(current, message));
    }
    chain.add(current);
    for (final String key : path) {
      final Optional<DocNode> next = document.get(current, key, View.RESOLVED);
      if (next.isEmpty()) {
        break;
      }
      current = next.get();
      chain.add(current);
    }
    final boolean complete = chain.size() == path.length + 1;
    if (complete && truthy(current)) {
      return List.of();
    }
    return List.of(Findings.atValue(current, message));
  }
}
