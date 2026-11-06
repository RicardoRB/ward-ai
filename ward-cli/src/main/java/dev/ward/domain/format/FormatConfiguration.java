package dev.ward.domain.format;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;

/** Resolves the formatting settings of one project file from its configuration. */
@FunctionalInterface
public interface FormatConfiguration {

  /**
   * Resolves the settings that apply to one file.
   *
   * @param relativePath the file path relative to the project root, with {@code /} separators
   * @param language the language of the file
   * @param declared the options the language's formatter declares
   * @return the resolved settings
   * @throws FormatConfigurationException if a configuration file sets an invalid option
   */
  FormatSettings resolve(
      String relativePath, AnalysisLanguage language, List<FormatOption> declared);
}
