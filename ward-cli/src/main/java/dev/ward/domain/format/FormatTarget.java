package dev.ward.domain.format;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.Objects;

/**
 * One file selected for formatting.
 *
 * @param relativePath the path relative to the project root, with {@code /} separators
 * @param language the language of the file
 * @param settings the settings that apply to the file
 */
public record FormatTarget(
    String relativePath, AnalysisLanguage language, FormatSettings settings) {

  /**
   * Checks the components.
   *
   * @throws NullPointerException if a component is missing
   */
  public FormatTarget {
    Objects.requireNonNull(relativePath, "relativePath");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(settings, "settings");
  }
}
