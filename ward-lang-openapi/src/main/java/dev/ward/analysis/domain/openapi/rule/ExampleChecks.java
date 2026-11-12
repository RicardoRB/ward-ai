package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.openapi.DocNode;
import dev.ward.analysis.domain.openapi.OpenApiDocument;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator.Dialect;
import dev.ward.analysis.domain.openapi.schema.JsonSchemaValidator.Relax;
import dev.ward.analysis.domain.openapi.schema.Pointers;
import dev.ward.analysis.domain.openapi.schema.SchemaError;
import dev.ward.analysis.domain.openapi.schema.SchemaMessages;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Checks values against schemas for one run of an example rule and collects the findings. */
final class ExampleChecks {

  private static final List<String> CONTAINERS = List.of("content", "headers", "parameters");

  private final OpenApiDocument document;
  private final List<OpenApiFinding> findings = new ArrayList<>();

  ExampleChecks(final OpenApiDocument document) {
    this.document = document;
  }

  List<OpenApiFinding> findings() {
    return findings;
  }

  /** Checks {@code value}, which a document wrote as the {@code property} of a node. */
  void validate(
      final DocNode schema, final DocNode value, final String property, final Relax relax) {
    if (cyclic(schema, Collections.newSetFromMap(new IdentityHashMap<>()))) {
      return;
    }
    final JsonSchemaValidator validator =
        new JsonSchemaValidator(
                document.isVersion31() ? Dialect.DRAFT2020 : Dialect.DRAFT4,
                false,
                holder -> document.target(holder).map(document::resolve))
            .relaxing(relax);
    final List<SchemaError> errors;
    try {
      errors = validator.validate(schema, value);
    } catch (final JsonSchemaValidator.UnresolvedReferenceException exception) {
      return;
    }
    for (final SchemaMessages.Message message :
        SchemaMessages.format(errors, value, List.of(property))) {
      findings.add(Findings.atValue(closest(value, message.path()), message.text()));
    }
  }

  /** Tells whether the schema contains itself, which Spectral leaves as an unresolved ref. */
  private boolean cyclic(final DocNode node, final Set<DocNode> onPath) {
    final DocNode resolved = document.resolve(node);
    if (!onPath.add(resolved)) {
      return true;
    }
    final boolean found = resolved.children().stream().anyMatch(child -> cyclic(child, onPath));
    onPath.remove(resolved);
    return found;
  }

  /** The node at {@code path} below {@code root}, or the deepest one that exists. */
  private static DocNode closest(final DocNode root, final List<String> path) {
    DocNode current = root;
    for (final String raw : path) {
      final Optional<DocNode> next =
          Pointers.child(current, raw.replace("~1", "/").replace("~0", "~"));
      if (next.isEmpty()) {
        break;
      }
      current = next.get();
    }
    return current;
  }

  /** Tells whether the node is below a section whose examples a rule reads. */
  static boolean inside(final List<String> path, final boolean schemasToo) {
    final boolean inSchemas =
        schemasToo
            && path.size() > 2
            && "components".equals(path.get(0))
            && "schemas".equals(path.get(1));
    return inSchemas
        || path.subList(0, Math.max(0, path.size() - 1)).stream().anyMatch(CONTAINERS::contains);
  }

  /** What a request or a response may leave out of {@code required}, by where the node is. */
  static Relax relax(final List<String> path) {
    if (starts(path, "components", "requestBodies")
        || starts(path, "paths", "*", "*", "requestBody")) {
      return Relax.READ_ONLY;
    }
    if (starts(path, "components", "responses") || starts(path, "paths", "*", "*", "responses")) {
      return Relax.WRITE_ONLY;
    }
    return Relax.NONE;
  }

  private static boolean starts(final List<String> path, final String... prefix) {
    if (path.size() < prefix.length) {
      return false;
    }
    for (int index = 0; index < prefix.length; index++) {
      if (!"*".equals(prefix[index]) && !prefix[index].equals(path.get(index))) {
        return false;
      }
    }
    return true;
  }
}
