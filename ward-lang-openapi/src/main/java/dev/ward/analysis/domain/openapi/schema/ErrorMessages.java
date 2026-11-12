package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.List;
import java.util.Optional;

/** The {@code errorMessage} keyword of ajv-errors, in its form that maps keywords to messages. */
final class ErrorMessages {

  private ErrorMessages() {}

  /**
   * Replaces the errors that the keywords of {@code errorMessage} raised for this schema, at the
   * same instance path, by one error with the message.
   *
   * @param before how many errors there were before the schema was checked
   */
  static void apply(
      final JsonSchemaValidator validator,
      final DocNode schema,
      final String path,
      final int before) {
    final Optional<DocNode> messages = schema.get("errorMessage");
    if (messages.isEmpty() || messages.get().kind() != DocNode.Kind.OBJECT) {
      return;
    }
    final List<SchemaError> errors = validator.errors();
    for (final DocNode.Entry entry : messages.get().entries()) {
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
}
