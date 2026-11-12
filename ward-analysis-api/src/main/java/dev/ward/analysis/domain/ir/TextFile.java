package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * One text file handed to a rule that reads text, with no language and no syntax tree.
 *
 * @param path project-root-relative path with {@code /} separators
 * @param text the full text of the file
 */
public record TextFile(String path, String text) {

  /**
   * Validates the required fields and the path separator.
   *
   * @throws IllegalArgumentException if the path contains a backslash
   */
  public TextFile {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(text, "text");
    if (path.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("path must use / separators");
    }
  }
}
