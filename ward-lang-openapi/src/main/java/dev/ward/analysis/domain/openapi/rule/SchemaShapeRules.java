package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** The rules about the shape of schemas: property names, required fields, types, combinators. */
final class SchemaShapeRules {

  private static final Pattern CAMEL = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
  private static final Pattern PASCAL = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");
  private static final Pattern KEBAB = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)+$");
  private static final Pattern SNAKE = Pattern.compile("^[a-z0-9]+(_[a-z0-9]+)+$");
  private static final Pattern SCREAMING = Pattern.compile("^[A-Z0-9]+(_[A-Z0-9]+)+$");
  private static final List<String> COMBINATORS = List.of("allOf", "anyOf", "oneOf");
  private static final List<String> SIBLINGS =
      List.of(
          "description",
          "title",
          "default",
          "example",
          "externalDocs",
          "nullable",
          "readOnly",
          "writeOnly",
          "deprecated",
          "xml",
          "enum");

  private SchemaShapeRules() {}

  /** {@code camel-case-properties}: property names are camelCase. */
  static List<OpenApiFinding> camelCase(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      for (final DocNode.Entry property :
          schema.get("properties").map(DocNode::entries).orElse(List.of())) {
        if (!CAMEL.matcher(property.key()).matches()) {
          findings.add(
              Findings.in(property.value(), property.keySpan(), caseMessage(property.key())));
        }
      }
    }
    return findings;
  }

  /** {@code required-fields-defined}: every required field is a defined property. */
  static List<OpenApiFinding> requiredDefined(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      findings.addAll(undefinedRequired(document, schema));
    }
    return findings;
  }

  private static List<OpenApiFinding> undefinedRequired(
      final OpenApiDocument document, final DocNode schema) {
    final List<DocNode> required = schema.get("required").map(DocNode::items).orElse(List.of());
    if (required.isEmpty()) {
      return List.of();
    }
    final Set<String> names = new HashSet<>();
    final Span key =
        schema.entries().stream()
            .filter(entry -> "required".equals(entry.key()))
            .findFirst()
            .orElseThrow()
            .keySpan();
    if (!collectProperties(document, schema, names, new HashSet<>())) {
      return List.of(
          Findings.in(schema, key, "object contains `required` fields but no `properties`"));
    }
    return required.stream()
        .filter(field -> !names.contains(field.text()))
        .map(
            field ->
                Findings.in(
                    schema,
                    key,
                    "`required` field `" + field.text() + "` is not defined in `properties`"))
        .toList();
  }

  /** {@code oas-missing-type}: a schema, and each of its properties, declares a type. */
  static List<OpenApiFinding> missingType(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      if (untyped(schema)) {
        findings.add(Findings.atValue(schema, "schema is missing a `type` field"));
      }
      for (final DocNode.Entry property :
          schema.get("properties").map(DocNode::entries).orElse(List.of())) {
        final DocNode value = document.resolve(property.value());
        if (value.kind() == DocNode.Kind.OBJECT && untyped(value)) {
          findings.add(
              Findings.atValue(
                  value, "schema property: `" + property.key() + "` is missing a `type` field"));
        }
      }
    }
    return findings;
  }

  /** {@code no-unnecessary-combinator}: a combinator has more than one item. */
  static List<OpenApiFinding> unnecessaryCombinator(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      for (final String combinator : COMBINATORS) {
        final Optional<DocNode> items = schema.get(combinator);
        if (items.isPresent()
            && items.get().items().size() == 1
            && !keptForSiblings(document, schema, combinator, items.get())) {
          findings.add(
              Findings.at(
                  items.get(),
                  "schema with `"
                      + combinator
                      + "` combinator containing only one item should be replaced with the item"
                      + " directly"));
        }
      }
    }
    return findings;
  }

  private static boolean keptForSiblings(
      final OpenApiDocument document,
      final DocNode schema,
      final String combinator,
      final DocNode items) {
    final boolean siblings =
        SIBLINGS.stream().anyMatch(key -> schema.get(key).isPresent())
            || schema.entries().stream().anyMatch(entry -> entry.key().startsWith("x-"));
    return !document.isVersion31()
        && "allOf".equals(combinator)
        && siblings
        && items.items().getFirst().reference().isPresent();
  }

  private static boolean untyped(final DocNode schema) {
    final List<String> exempt =
        List.of(
            "type",
            "allOf",
            "anyOf",
            "oneOf",
            "const",
            "enum",
            "properties",
            "items",
            "additionalProperties",
            "patternProperties");
    return exempt.stream().noneMatch(key -> schema.get(key).isPresent());
  }

  /** Adds the property names of a schema and of its combinator branches; tells if any exist. */
  private static boolean collectProperties(
      final OpenApiDocument document,
      final DocNode schema,
      final Set<String> names,
      final Set<DocNode> visited) {
    final DocNode resolved = document.resolve(schema);
    if (!visited.add(resolved)) {
      return false;
    }
    final Optional<DocNode> properties = resolved.get("properties");
    properties.ifPresent(node -> node.entries().forEach(entry -> names.add(entry.key())));
    boolean found = properties.isPresent();
    for (final String combinator : COMBINATORS) {
      for (final DocNode branch : resolved.get(combinator).map(DocNode::items).orElse(List.of())) {
        found |= collectProperties(document, branch, names, visited);
      }
    }
    return found;
  }

  private static String caseMessage(final String name) {
    final String kind;
    if (PASCAL.matcher(name).matches()) {
      kind = "PascalCase";
    } else if (KEBAB.matcher(name).matches()) {
      kind = "kebab-case";
    } else if (SNAKE.matcher(name).matches()) {
      kind = "snake_case";
    } else if (SCREAMING.matcher(name).matches()) {
      kind = "SCREAMING_SNAKE_CASE";
    } else {
      return "property '" + name + "' is using an unclassified case, not camelCase";
    }
    return "property `" + name + "` is `" + kind + "` not `camelCase`";
  }
}
