package dev.ward.analysis.domain.api;

import java.util.Objects;

/**
 * Where a declaration is written.
 *
 * @param path the project-relative path of the file, with {@code /} separators
 * @param line the 1-based line
 * @param column the 1-based column
 */
public record ApiLocation(String path, int line, int column) {
  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the path is missing
   * @throws IllegalArgumentException if the line or column is not positive
   */
  public ApiLocation {
    Objects.requireNonNull(path, "path");
    if (line < 1 || column < 1) {
      throw new IllegalArgumentException("line and column are 1-based");
    }
  }
}
