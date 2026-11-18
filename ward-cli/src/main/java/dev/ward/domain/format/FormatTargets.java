package dev.ward.domain.format;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Lists the files of a project that the formatters may change, with their settings. */
public interface FormatTargets {
  /**
   * Lists the selected files, sorted by path, with each file's resolved settings.
   *
   * @param root the project root
   * @param declared the options each language's formatter declares
   * @return the selected files
   * @throws dev.ward.domain.config.WardException if a configuration file is invalid
   */
  List<FormatTarget> select(Path root, Map<AnalysisLanguage, List<FormatOption>> declared);

  /**
   * Finds the project root for a working directory: the nearest folder at or above it that holds
   * {@code ward.toml}, else the nearest that holds {@code .git}, else the directory itself.
   *
   * @param start the working directory
   * @return the project root
   */
  Path projectRoot(Path start);
}
