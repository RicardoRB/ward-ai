package dev.ward.analysis.domain.openapi.rule;

import dev.ward.analysis.domain.ir.Span;
import java.util.Objects;

/**
 * A violation of an OpenAPI rule.
 *
 * @param file the project-relative path of the file the node is in, which may be a referenced file
 * @param span where the finding points: the key of the node it is about
 * @param message what is wrong
 */
public record OpenApiFinding(String file, Span span, String message) {

  /**
   * Validates the parts.
   *
   * @throws NullPointerException if a part is missing
   */
  public OpenApiFinding {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(span, "span");
    Objects.requireNonNull(message, "message");
  }
}
