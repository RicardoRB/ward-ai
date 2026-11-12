package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The rules that read the document as written, before any reference is followed. */
final class RefRules {

  private static final String ITEMS = "items";
  private static final String TYPE = "type";
  private static final String ARRAY = "array";

  private RefRules() {}

  /** {@code no-ref-siblings}: in OpenAPI 3.0 a {@code $ref} stands alone in its object. */
  static List<OpenApiFinding> noSiblings(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    if (document.isVersion31()) {
      return findings;
    }
    for (final Nodes.Located located : Nodes.raw(document.root())) {
      final DocNode node = located.node();
      if (node.kind() == DocNode.Kind.OBJECT && node.get(OpenApiDocument.REF).isPresent()) {
        for (final DocNode.Entry entry : node.entries()) {
          if (!OpenApiDocument.REF.equals(entry.key())) {
            findings.add(
                Findings.atValue(
                    entry.value(), "$ref must not be placed next to any other properties"));
          }
        }
      }
    }
    return findings;
  }

  /** {@code array-items}: a schema of {@code type: array} has {@code items}. */
  static List<OpenApiFinding> arrayItems(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    for (final Nodes.Located located : Nodes.raw(document.root())) {
      final DocNode node = located.node();
      if (isArray(document, node)) {
        findings.addAll(
            Fields.requireTruthy(
                document,
                node,
                "Schemas with \"type: array\", require a sibling \"items\" field",
                ITEMS));
      }
    }
    return findings;
  }

  private static boolean isArray(final OpenApiDocument document, final DocNode node) {
    final Optional<DocNode> type = node.get(TYPE);
    if (type.isEmpty() || node.kind() != DocNode.Kind.OBJECT) {
      return false;
    }
    if (type.get().kind() == DocNode.Kind.STRING) {
      return ARRAY.equals(type.get().text());
    }
    return document.isVersion31()
        && type.get().kind() == DocNode.Kind.ARRAY
        && type.get().items().stream().anyMatch(item -> ARRAY.equals(item.text()));
  }
}
