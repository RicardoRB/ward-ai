package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** The OWASP rules about the limits of arrays, strings and integers, and about open objects. */
final class OwaspSchemaRules {

  private static final String TYPE = "type";

  private OwaspSchemaRules() {}

  /** {@code owasp-api4-2023-array-limit}: an array schema has {@code maxItems}. */
  static List<OpenApiFinding> arrayLimit(final OpenApiDocument document) {
    return require(
        document,
        "array",
        schema -> schema.get("maxItems").isPresent(),
        "Schema of type array must specify maxItems.");
  }

  /** {@code owasp-api4-2023-string-limit}: a string schema has a maximum length, enum or const. */
  static List<OpenApiFinding> stringLimit(final OpenApiDocument document) {
    return require(
        document,
        "string",
        schema -> hasAny(schema, "maxLength", "enum", "const"),
        "Schema of type string must specify maxLength, enum, or const.");
  }

  /** {@code owasp-api4-2023-string-restricted}: a string schema has a format, pattern or enum. */
  static List<OpenApiFinding> stringRestricted(final OpenApiDocument document) {
    return require(
        document,
        "string",
        schema -> hasAny(schema, "format", "pattern", "enum", "const"),
        "Schema of type string should specify a format, pattern, enum, or const.");
  }

  /** {@code owasp-api4-2023-integer-limit}: in 3.1 an integer has exactly one bound each way. */
  static List<OpenApiFinding> integerLimit(final OpenApiDocument document) {
    return document.isVersion31()
        ? require(
            document,
            "integer",
            schema ->
                exactlyOne(schema, "minimum", "exclusiveMinimum")
                    && exactlyOne(schema, "maximum", "exclusiveMaximum"),
            "Schema of type integer must specify minimum and maximum.")
        : List.of();
  }

  /** {@code owasp-api4-2023-integer-limit-legacy}: in 3.0 an integer has both bounds. */
  static List<OpenApiFinding> integerLimitLegacy(final OpenApiDocument document) {
    return document.isVersion31()
        ? List.of()
        : require(
            document,
            "integer",
            schema -> schema.get("minimum").isPresent() && schema.get("maximum").isPresent(),
            "Schema of type integer must specify minimum and maximum.");
  }

  /** {@code owasp-api4-2023-integer-format}: an integer schema has a {@code format}. */
  static List<OpenApiFinding> integerFormat(final OpenApiDocument document) {
    return require(
        document,
        "integer",
        schema -> schema.get("format").isPresent(),
        "Schema of type integer must specify format (int32 or int64).");
  }

  private static List<OpenApiFinding> require(
      final OpenApiDocument document,
      final String type,
      final Predicate<DocNode> satisfied,
      final String message) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Nodes.Located located : Nodes.resolved(document, document.root())) {
      final DocNode node = located.node();
      if (hasType(document, node, type) && !satisfied.test(node)) {
        findings.add(Findings.atValue(node, message));
      }
    }
    return findings;
  }

  private static boolean hasType(
      final OpenApiDocument document, final DocNode node, final String type) {
    final DocNode declared =
        node.kind() == DocNode.Kind.OBJECT ? node.get(TYPE).orElse(null) : null;
    if (declared == null) {
      return false;
    }
    if (declared.kind() == DocNode.Kind.STRING) {
      return type.equals(declared.text());
    }
    return document.isVersion31()
        && declared.kind() == DocNode.Kind.ARRAY
        && declared.items().stream().anyMatch(item -> type.equals(item.text()));
  }

  private static boolean hasAny(final DocNode node, final String... keys) {
    for (final String key : keys) {
      if (node.get(key).isPresent()) {
        return true;
      }
    }
    return false;
  }

  private static boolean exactlyOne(final DocNode node, final String first, final String second) {
    return node.get(first).isPresent() != node.get(second).isPresent();
  }
}
