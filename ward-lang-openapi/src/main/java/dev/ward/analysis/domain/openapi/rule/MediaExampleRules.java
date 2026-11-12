package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator.Relax;
import java.util.List;
import java.util.Optional;

/** {@code oas3-valid-media-example}: the examples of a media type, header or parameter fit. */
final class MediaExampleRules {

  private static final String EXAMPLE = "example";
  private static final String EXAMPLES = "examples";

  private MediaExampleRules() {}

  /** Checks the example, or else the examples, of every node that has a schema. */
  static List<OpenApiFinding> validMediaExample(final OpenApiDocument document) {
    final ExampleChecks checks = new ExampleChecks(document);
    for (final Nodes.Located located : Nodes.resolved(document, document.root())) {
      final DocNode node = located.node();
      final Optional<DocNode> schema = node.get("schema");
      if (ExampleChecks.inside(located.path(), false)
          && node.kind() == DocNode.Kind.OBJECT
          && schema.filter(Fields::truthy).isPresent()
          && (node.get(EXAMPLE).isPresent()
              || node.get(EXAMPLES).filter(Fields::truthy).isPresent())) {
        media(checks, node, schema.get(), ExampleChecks.relax(located.path()));
      }
    }
    return checks.findings();
  }

  private static void media(
      final ExampleChecks checks, final DocNode node, final DocNode schema, final Relax relax) {
    final Optional<DocNode> single = node.get(EXAMPLE);
    if (single.isPresent()) {
      checks.validate(schema, single.get(), EXAMPLE, relax);
      return;
    }
    final DocNode examples = node.get(EXAMPLES).orElseThrow();
    if (examples.kind() == DocNode.Kind.OBJECT) {
      examples.entries().forEach(entry -> keyed(checks, schema, entry, relax));
    }
  }

  private static void keyed(
      final ExampleChecks checks,
      final DocNode schema,
      final DocNode.Entry entry,
      final Relax relax) {
    final DocNode example = entry.value();
    if (example.kind() != DocNode.Kind.OBJECT || example.get("externalValue").isPresent()) {
      return;
    }
    final Optional<DocNode> value = example.get("value");
    if (value.isPresent()) {
      checks.validate(schema, value.get(), "value", relax);
    } else {
      checks.findings().add(Findings.atValue(example, missing(entry.key())));
    }
  }

  /** Spectral prints a numeric key in brackets. */
  private static String missing(final String key) {
    final String name = key.chars().allMatch(Character::isDigit) ? "[" + key + "]" : key;
    return "\"" + name + ".value\" property must exist";
  }
}
