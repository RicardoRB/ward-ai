package dev.ward.analysis.domain.openapi;

import java.util.Objects;

/**
 * A reason a document could not be loaded.
 *
 * @param file the project-relative path of the file with the problem
 * @param line 1-based line of the problem
 * @param column 1-based column of the problem
 * @param message what is wrong
 */
public record LoadError(String file, int line, int column, String message) {

  /**
   * Validates the parts.
   *
   * @throws NullPointerException if the file or message is missing
   * @throws IllegalArgumentException if the line or column is not positive
   */
  public LoadError {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(message, "message");
    if (line < 1 || column < 1) {
      throw new IllegalArgumentException("line and column are 1-based");
    }
  }

  /** An error at the start of {@code from}, or at the start of {@code fallback} when it is null. */
  static LoadError at(final DocNode from, final String fallback, final String message) {
    if (from == null) {
      return new LoadError(fallback, 1, 1, message);
    }
    return new LoadError(from.file(), from.span().startLine(), from.span().startColumn(), message);
  }

  /** Returns the error as {@code file:line:column: message}. */
  public String describe() {
    return file + ":" + line + ":" + column + ": " + message;
  }
}
