package dev.ward.analysis.domain.ir;

import java.util.Objects;

/**
 * One source file handed to a language frontend.
 *
 * @param path project-root-relative path with {@code /} separators
 * @param language the language the file is written in
 * @param text the full source text
 */
public record SourceUnit(String path, AnalysisLanguage language, String text) {

  /**
   * Validates the required fields and the path separator.
   *
   * @throws IllegalArgumentException if the path contains a backslash
   */
  public SourceUnit {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(text, "text");
    if (path.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("path must use / separators");
    }
  }
}
