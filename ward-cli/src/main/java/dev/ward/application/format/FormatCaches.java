package dev.ward.application.format;

import dev.ward.analysis.domain.port.AnalysisCache;
import java.nio.file.Path;

/** Opens the result cache of the {@code ward-format} gate for one project. */
@FunctionalInterface
public interface FormatCaches {

  /**
   * Returns the cache that keeps the formatting results of the project at {@code projectRoot}.
   *
   * @param projectRoot the project root
   * @return the cache
   */
  AnalysisCache open(Path projectRoot);
}
