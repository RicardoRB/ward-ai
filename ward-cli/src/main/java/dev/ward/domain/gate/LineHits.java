package dev.ward.domain.gate;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The per-line coverage that one report holds, entry by entry as the report wrote them.
 *
 * @param report the path of the report file that was read
 * @param entries one entry per source file the report names
 * @param recordsBranches whether the report measures branches at all, even when it has none to
 *     cover
 */
public record LineHits(Path report, List<Entry> entries, boolean recordsBranches) {

  /** Requires the report and copies the entries. */
  public LineHits {
    Objects.requireNonNull(report, "report");
    entries = List.copyOf(entries);
  }

  /** Creates the lines of a report that records no branches. */
  public LineHits(final Path report, final List<Entry> entries) {
    this(report, entries, false);
  }

  /** How a report names a source file, which decides how Ward finds the file in the project. */
  public enum Kind {
    /** A file path, as an lcov {@code SF:} record writes it. */
    PATH,
    /** A package directory and a file name, joined by a slash, as JaCoCo writes them. */
    PACKAGE_FILE
  }

  /**
   * The lines of one source file named by a report.
   *
   * @param kind how {@code name} names the file
   * @param name the path, or the package directory and file name, as the report wrote it
   * @param lines the lines the report marks in that file
   */
  public record Entry(Kind kind, String name, FileLines lines) {}
}
