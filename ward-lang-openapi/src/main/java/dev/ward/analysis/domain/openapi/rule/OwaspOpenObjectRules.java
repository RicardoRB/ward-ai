package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;

/** The OWASP rules about objects that accept properties their schema does not name. */
final class OwaspOpenObjectRules {

  private static final String TYPE = "type";

  private OwaspOpenObjectRules() {}

  /** {@code owasp-api3-2023-no-additional-properties}: in 3.0 an object is closed. */
  static List<OpenApiFinding> noAdditionalProperties(final OpenApiDocument document) {
    return document.isVersion31() ? List.of() : open(document, "additionalProperties", false);
  }

  /** {@code owasp-api3-2023-constrained-additional-properties}: a typed map has a limit. */
  static List<OpenApiFinding> constrainedAdditionalProperties(final OpenApiDocument document) {
    return document.isVersion31() ? List.of() : open(document, "additionalProperties", true);
  }

  /** {@code owasp-api3-2023-no-unevaluated-properties}: in 3.1 an object is closed. */
  static List<OpenApiFinding> noUnevaluatedProperties(final OpenApiDocument document) {
    return document.isVersion31() ? open(document, "unevaluatedProperties", false) : List.of();
  }

  /** {@code owasp-api3-2023-constrained-unevaluated-properties}: a typed map has a limit. */
  static List<OpenApiFinding> constrainedUnevaluatedProperties(final OpenApiDocument document) {
    return document.isVersion31() ? open(document, "unevaluatedProperties", true) : List.of();
  }

  /**
   * The objects with a truthy keyword: with {@code constrained}, those whose value is a schema and
   * that lack {@code maxProperties}; without it, the keyword itself, which must be falsy.
   */
  private static List<OpenApiFinding> open(
      final OpenApiDocument document, final String keyword, final boolean constrained) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Nodes.Located located : Nodes.resolved(document, document.root())) {
      final DocNode node = located.node();
      final DocNode value = node.get(keyword).orElse(null);
      if (value == null || !isObjectType(node) || !Fields.truthy(value)) {
        continue;
      }
      final boolean literal = value.kind() == DocNode.Kind.BOOLEAN;
      if (constrained) {
        if (!literal && node.get("maxProperties").isEmpty()) {
          findings.add(
              Findings.atValue(node, "Objects should not allow unconstrained " + keyword + "."));
        }
      } else {
        findings.add(
            Findings.atValue(
                value, "If the " + keyword + " keyword is used it must be set to false."));
      }
    }
    return findings;
  }

  private static boolean isObjectType(final DocNode node) {
    return node.kind() == DocNode.Kind.OBJECT
        && node.get(TYPE).filter(type -> "object".equals(type.text())).isPresent();
  }
}
