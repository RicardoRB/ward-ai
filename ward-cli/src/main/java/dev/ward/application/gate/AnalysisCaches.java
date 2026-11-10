package dev.ward.application.gate;

import dev.ward.analysis.domain.port.AnalysisCache;
import java.nio.file.Path;

/** Port that opens the analysis result cache of one project. */
@FunctionalInterface
public interface AnalysisCaches {

  /** Returns the cache that keeps the analysis results of the project at {@code projectRoot}. */
  AnalysisCache open(Path projectRoot);
}
