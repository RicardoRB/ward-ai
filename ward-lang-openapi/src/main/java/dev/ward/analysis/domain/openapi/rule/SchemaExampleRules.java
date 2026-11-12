package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator.Relax;
import java.util.List;

/** {@code oas3-valid-schema-example}: the example and default of a schema fit the schema. */
final class SchemaExampleRules {

  private static final List<String> SHAPE =
      List.of("enum", "type", "format", "$ref", "properties", "items");

  private SchemaExampleRules() {}

  /** Checks the {@code example} and the {@code default} of every schema that has one. */
  static List<OpenApiFinding> validSchemaExample(final OpenApiDocument document) {
    final ExampleChecks checks = new ExampleChecks(document);
    for (final Nodes.Located located : Nodes.resolved(document, document.root())) {
      final DocNode node = located.node();
      if (!"properties".equals(located.key())
          && ExampleChecks.inside(located.path(), true)
          && node.kind() == DocNode.Kind.OBJECT
          && hasExample(node)
          && SHAPE.stream().anyMatch(key -> node.get(key).filter(Fields::truthy).isPresent())) {
        for (final String field : List.of("example", "default")) {
          node.get(field).ifPresent(value -> checks.validate(node, value, field, Relax.NONE));
        }
      }
    }
    return checks.findings();
  }

  private static boolean hasExample(final DocNode node) {
    return node.get("example").isPresent() || node.get("default").isPresent();
  }
}
