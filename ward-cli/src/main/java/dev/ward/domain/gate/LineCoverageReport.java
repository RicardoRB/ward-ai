package dev.ward.domain.gate;

import java.nio.file.Path;

/** Reads the coverage of each line of each source file from one coverage report file. */
public interface LineCoverageReport {

  /** Tells whether a report that starts with {@code head} is in the format this reader parses. */
  boolean accepts(String head);

  /**
   * Returns the lines the report at {@code report} marks.
   *
   * @throws LineCoverageException if the file cannot be read or is not in a format this reader
   *     parses
   */
  LineHits read(Path report);
}
