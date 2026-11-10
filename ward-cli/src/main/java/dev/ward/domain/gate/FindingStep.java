package dev.ward.domain.gate;

import java.util.Objects;

/**
 * One step of a finding's path, such as its source, a propagating assignment or its sink.
 *
 * @param file project-root-relative path with {@code /} separators
 * @param line 1-based line of the step
 * @param column 1-based column of the step
 * @param message what happens at the step
 */
public record FindingStep(String file, int line, int column, String message) {

  /**
   * Validates the fields.
   *
   * @throws NullPointerException if the file or message is missing
   * @throws IllegalArgumentException if the line or column is not positive
   */
  public FindingStep {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(message, "message");
    if (line < 1 || column < 1) {
      throw new IllegalArgumentException("line and column are 1-based");
    }
  }
}
