package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.schema.BundledSchemas;
import dev.ward.analysis.domain.openapi.schema.DocumentSchemaMessages;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator.Dialect;
import dev.ward.analysis.domain.openapi.schema.Pointers;
import dev.ward.analysis.domain.openapi.schema.SchemaError;
import dev.ward.analysis.domain.openapi.schema.SchemaMessages;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** {@code oas3-schema}: the document, as written, follows the schema of its OpenAPI version. */
final class DocumentSchemaRules {

  private DocumentSchemaRules() {}

  /** Validates the entry file against the bundled schema of its OpenAPI version. */
  static List<OpenApiFinding> oas3Schema(final OpenApiDocument document) {
    final List<OpenApiFinding> findings = new ArrayList<>();
    final DocNode schema = document.isVersion31() ? BundledSchemas.oas31() : BundledSchemas.oas30();
    final List<SchemaError> errors =
        new JsonSchemaValidator(Dialect.DRAFT2020, true, BundledSchemas.resolver())
            .validate(schema, document.root());
    for (final SchemaMessages.Message message :
        DocumentSchemaMessages.format(errors, document.root())) {
      findings.add(
          Findings.atValue(closest(document.root(), message.path()), message.text() + "."));
    }
    return findings;
  }

  /** The node at {@code path} of the document as written, or the deepest one that exists. */
  private static DocNode closest(final DocNode root, final List<String> path) {
    DocNode current = root;
    for (final String key : path) {
      final Optional<DocNode> next = Pointers.child(current, key);
      if (next.isEmpty()) {
        break;
      }
      current = next.get();
    }
    return current;
  }
}
