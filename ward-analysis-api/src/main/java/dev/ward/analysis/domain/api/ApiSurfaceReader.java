package dev.ward.analysis.domain.api;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.port.LanguageFrontend;
import java.util.List;
import java.util.function.Predicate;

/** Reads the declared API of the source files of one language. */
public interface ApiSurfaceReader {

  /** Returns the language of the files this reader reads. */
  AnalysisLanguage language();

  /**
   * Returns the types the units declare that are part of an API, with their API members, ordered by
   * qualified name.
   *
   * @param units the source files, all of {@link #language()}
   * @param frontend parses a unit
   * @param libraryExists tells whether a class outside the units exists, by binary name
   * @throws ApiSurfaceException if a unit has a syntax error
   */
  List<ApiType> read(
      List<SourceUnit> units, LanguageFrontend frontend, Predicate<String> libraryExists);
}
