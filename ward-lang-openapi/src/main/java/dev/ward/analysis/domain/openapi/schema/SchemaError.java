package dev.ward.analysis.domain.openapi.schema;

import dev.ward.analysis.domain.openapi.DocNode;
import java.util.List;

/**
 * A failure of a JSON Schema keyword, shaped like an ajv error.
 *
 * @param keyword the keyword that failed, such as {@code type} or {@code required}
 * @param instancePath the JSON pointer of the failing value inside the validated data
 * @param message the ajv message, such as {@code must be string}
 * @param detail the missing or additional property name, or empty
 * @param allowed the allowed values of an {@code enum}, or empty
 * @param combinatorUnmatched whether a {@code oneOf} or {@code anyOf} had no passing schema
 */
public record SchemaError(
    String keyword,
    String instancePath,
    String message,
    String detail,
    List<DocNode> allowed,
    boolean combinatorUnmatched) {

  /** Copies the allowed values. */
  public SchemaError {
    allowed = List.copyOf(allowed);
  }

  /** An error with nothing but a message. */
  static SchemaError of(final String keyword, final String instancePath, final String message) {
    return new SchemaError(keyword, instancePath, message, "", List.of(), false);
  }

  /** An error about the property {@code detail}. */
  static SchemaError about(
      final String keyword, final String path, final String message, final String detail) {
    return new SchemaError(keyword, path, message, detail, List.of(), false);
  }
}
