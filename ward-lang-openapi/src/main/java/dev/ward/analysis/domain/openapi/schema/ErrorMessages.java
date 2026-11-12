package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.List;
import java.util.Optional;

/** The {@code errorMessage} keyword of ajv-errors, in its form that maps keywords to messages. */
final class ErrorMessages {

  private ErrorMessages() {}

  /**
   * Replaces the errors of this schema by the messages of {@code errorMessage}: a text replaces all
   * of them, an object replaces the errors of each keyword it names, at the same instance path.
   *
   * @param before how many errors there were before the schema was checked
   */
  static void apply(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final String path,
      final int before) {
    final Optional<DocNode> messages = schema.get("errorMessage");
    if (messages.isEmpty()) {
      return;
    }
    final List<SchemaError> errors = validator.getErrors();
    if (messages.get().kind() == DocNode.Kind.STRING && errors.size() > before) {
      while (errors.size() > before) {
        errors.removeLast();
      }
      errors.add(SchemaError.of("errorMessage", path, messages.get().text()));
    } else if (messages.get().kind() == DocNode.Kind.OBJECT) {
      messages.get().entries().forEach(entry -> byKeyword(errors, entry, path, before));
    }
  }

  private static void byKeyword(
      final List<SchemaError> errors,
      final DocNode.Entry entry,
      final String path,
      final int before) {
    boolean matched = false;
    for (int index = errors.size() - 1; index >= before; index--) {
      final SchemaError error = errors.get(index);
      if (error.keyword().equals(entry.key()) && error.instancePath().equals(path)) {
        errors.remove(index);
        matched = true;
      }
    }
    if (matched) {
      errors.add(SchemaError.of("errorMessage", path, entry.value().text()));
    }
  }
}
