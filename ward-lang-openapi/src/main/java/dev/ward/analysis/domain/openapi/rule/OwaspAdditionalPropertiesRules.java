package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The OWASP rules about the extra properties of request objects. */
final class OwaspAdditionalPropertiesRules {

  private static final String TYPE = "type";

  private OwaspAdditionalPropertiesRules() {}

  /** {@code owasp-no-additionalProperties}: a request object closes or types its extras. */
  static List<OpenApiFinding> noAdditionalProperties(final OpenApiDocument document) {
    final Set<DocNode> request = SchemaWalk.requestSchemas(document);
    final Set<DocNode> combined = SchemaWalk.combined(document);
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      final Optional<DocNode> extra = schema.get("additionalProperties");
      final boolean open = extra.isEmpty() || "true".equals(extra.get().text());
      final boolean composed =
          extra.isEmpty()
              && (combined.contains(schema)
                  || List.of("allOf", "anyOf", "oneOf").stream()
                      .anyMatch(key -> schema.get(key).isPresent()));
      if (request.contains(schema) && isObject(schema) && open && !composed && !finite(schema)) {
        findings.add(
            Findings.in(
                schema,
                OwaspSupport.key(schema, anchorKey(schema)),
                "request objects should set `additionalProperties` to `false` or define a schema for"
                    + " additional values"));
      }
    }
    return findings;
  }

  /** {@code owasp-constrained-additionalProperties}: a request map has {@code maxProperties}. */
  static List<OpenApiFinding> constrainedAdditionalProperties(final OpenApiDocument document) {
    final Set<DocNode> request = SchemaWalk.requestSchemas(document);
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final DocNode schema : SchemaWalk.schemas(document)) {
      final Optional<DocNode> extra = schema.get("additionalProperties");
      if (request.contains(schema)
          && isObject(schema)
          && !finite(schema)
          && schema.get("maxProperties").isEmpty()
          && extra.isPresent()
          && !"false".equals(extra.get().text())) {
        findings.add(
            Findings.in(
                schema,
                OwaspSupport.key(schema, anchorKey(schema)),
                "request maps should define `maxProperties` to limit the number of properties"));
      }
    }
    return findings;
  }

  private static boolean isObject(final DocNode schema) {
    return OwaspSupport.declares(schema, "object")
        || schema.get(TYPE).isEmpty()
            && (schema.get("properties").isPresent()
                || schema.get("additionalProperties").isPresent());
  }

  private static boolean finite(final DocNode schema) {
    return schema.get("enum").isPresent();
  }

  private static String anchorKey(final DocNode schema) {
    if (schema.get(TYPE).isPresent()) {
      return TYPE;
    }
    return schema.get("properties").isPresent() ? "properties" : "additionalProperties";
  }
}
