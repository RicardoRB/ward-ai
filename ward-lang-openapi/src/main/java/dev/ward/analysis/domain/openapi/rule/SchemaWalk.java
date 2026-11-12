package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.OpenApiParts.Operation;
import dev.ward.analysis.domain.openapi.OperationNodes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Finds the schemas of a document: every schema object, resolved, each visited once. */
final class SchemaWalk {

  private static final List<String> SINGLE =
      List.of("items", "additionalProperties", "not", "contains", "if", "then", "else");
  private static final List<String> LISTS = List.of("allOf", "anyOf", "oneOf", "prefixItems");
  private static final List<String> MAPS = List.of("properties", "patternProperties");
  private static final List<String> CARRIERS =
      List.of("parameters", "requestBodies", "responses", "headers");

  private SchemaWalk() {}

  /** Returns the resolved schema objects of {@code document}, in discovery order. */
  static List<DocNode> schemas(final OpenApiDocument document) {
    final List<DocNode> roots = new ArrayList<>();
    for (final DocNode.Entry entry : document.parts().component("schemas")) {
      roots.add(entry.value());
    }
    for (final String carrier : CARRIERS) {
      for (final DocNode.Entry entry : document.parts().component(carrier)) {
        carried(document, entry.value(), roots);
      }
    }
    for (final DocNode.Entry path : document.parts().paths()) {
      OperationNodes.parameters(document.resolve(path.value()))
          .forEach(parameter -> carried(document, parameter, roots));
    }
    for (final Operation operation : document.parts().operations()) {
      operationRoots(document, operation.node(), roots);
    }
    return expand(document, roots);
  }

  private static void operationRoots(
      final OpenApiDocument document, final DocNode operation, final List<DocNode> roots) {
    OperationNodes.parameters(operation).forEach(parameter -> carried(document, parameter, roots));
    OperationNodes.requestBody(operation).ifPresent(body -> carried(document, body, roots));
    for (final DocNode.Entry response : OperationNodes.responses(operation)) {
      carried(document, response.value(), roots);
    }
  }

  /** The schemas a parameter, request body, response or header carries directly or in content. */
  private static void carried(
      final OpenApiDocument document, final DocNode carrier, final List<DocNode> roots) {
    final DocNode node = document.resolve(carrier);
    node.get("schema").ifPresent(roots::add);
    node.get("content")
        .map(document::resolve)
        .ifPresent(
            content ->
                content
                    .entries()
                    .forEach(
                        media ->
                            document.resolve(media.value()).get("schema").ifPresent(roots::add)));
    node.get("headers")
        .map(document::resolve)
        .ifPresent(
            headers ->
                headers.entries().forEach(header -> carried(document, header.value(), roots)));
  }

  private static List<DocNode> expand(final OpenApiDocument document, final List<DocNode> roots) {
    final List<DocNode> found = new ArrayList<>();
    final Set<DocNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    final Deque<DocNode> pending = new ArrayDeque<>(roots);
    while (!pending.isEmpty()) {
      final DocNode schema = document.resolve(pending.pollFirst());
      if (schema.kind() == DocNode.Kind.OBJECT && seen.add(schema)) {
        found.add(schema);
        pending.addAll(children(schema));
      }
    }
    return found;
  }

  private static List<DocNode> children(final DocNode schema) {
    final List<DocNode> children = new ArrayList<>();
    SINGLE.forEach(key -> schema.get(key).ifPresent(children::add));
    LISTS.forEach(key -> schema.get(key).ifPresent(list -> children.addAll(list.items())));
    for (final String key : MAPS) {
      schema.get(key).ifPresent(map -> map.entries().forEach(entry -> children.add(entry.value())));
    }
    return children;
  }
}
