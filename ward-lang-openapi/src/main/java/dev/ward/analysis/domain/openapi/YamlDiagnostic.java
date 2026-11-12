package dev.ward.analysis.domain.openapi;

import java.util.Objects;

/**
 * A problem found while reading a YAML or JSON document.
 *
 * @param message what is wrong
 * @param line 1-based line of the problem
 * @param column 1-based column of the problem
 */
public record YamlDiagnostic(String message, int line, int column) {

  /**
   * Validates the message and the position.
   *
   * @throws NullPointerException if the message is missing
   * @throws IllegalArgumentException if the line or column is not positive
   */
  public YamlDiagnostic {
    Objects.requireNonNull(message, "message");
    if (line < 1 || column < 1) {
      throw new IllegalArgumentException("line and column are 1-based");
    }
  }
}
