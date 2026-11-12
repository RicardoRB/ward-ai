package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.rule.ProjectFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Port that reads the project's source files for the analysis gate. */
@FunctionalInterface
public interface AnalysisSourceUnits {

  /**
   * Reads the source files under {@code root} that are written in one of {@code languages}, sorted
   * by project-relative path. Returns no units when {@code languages} is empty.
   */
  List<SourceUnit> read(Path root, Set<AnalysisLanguage> languages);

  /**
   * Reads the files named {@code names} under {@code root}, by project-relative path, skipping the
   * folders that source reads skip, such as {@code node_modules}. Returns no files by default.
   */
  default ProjectFiles manifests(final Path root, final Set<String> names) {
    return ProjectFiles.empty();
  }

  /**
   * Reads the text files under {@code root} for the {@code secrets} check, sorted by path. Hidden
   * folders are read; build output, {@code .git}, {@code .ward}, files that Git ignores and binary
   * files are not. A file that {@code excluded} accepts is not read at all. When {@code paths} is
   * not empty only those root-relative files are read, if they still exist. A file that cannot be
   * read, or is over the size limit, is an error in the result and never skipped. Returns nothing
   * by default.
   */
  default TextRead readText(
      final Path root, final List<String> paths, final Predicate<String> excluded) {
    return TextRead.EMPTY;
  }
}
