package dev.ward.application.gate.apicompat;

import dev.ward.analysis.domain.api.ApiSurfaceException;
import dev.ward.analysis.domain.api.ApiType;
import dev.ward.analysis.domain.ir.SourceUnit;
import java.nio.file.Path;
import java.util.List;

/** Outbound port that reads the API of source files; it parses them and knows the libraries. */
@FunctionalInterface
public interface ApiSurfaces {
  /**
   * Returns the API types of {@code units}, which belong to the project at {@code root}.
   *
   * @throws ApiSurfaceException if a unit has a syntax error
   */
  List<ApiType> read(Path root, List<SourceUnit> units);
}
