package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Port that reads the project's source files for the analysis gate. */
@FunctionalInterface
public interface AnalysisSourceUnits {

  /**
   * Reads the source files under {@code root} that are written in one of {@code languages}, sorted
   * by project-relative path. Returns no units when {@code languages} is empty.
   */
  List<SourceUnit> read(Path root, Set<AnalysisLanguage> languages);
}
