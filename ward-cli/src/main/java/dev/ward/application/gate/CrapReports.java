package dev.ward.application.gate;

import dev.ward.domain.gate.ConventionalCoverageReports;
import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineHits;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Finds, reads and checks the coverage reports of a {@code crap} or {@code coverage} check. */
final class CrapReports {
  private static final Logger LOGGER = Logger.getLogger(CrapReports.class.getName());

  /** The advice of a {@code crap} check whose report is missing. */
  static final String CRAP_FIRST =
      " The coverage check must run first, so that it writes the report.";

  /** The advice of a {@code coverage} check whose report is missing. */
  static final String TESTS_FIRST = " Run the test gate first, so that the tests write the report.";

  private final CrapPorts ports;
  private final String first;

  CrapReports(final CrapPorts ports, final String first) {
    this.ports = ports;
    this.first = first;
  }

  /**
   * Returns the reports of {@code project} that {@code options} names or the build tool writes,
   * read.
   *
   * @throws CrapReportException if a report is missing or cannot be parsed
   */
  List<LineHits> read(final Project project, final List<String> paths, final String gate) {
    return parse(project, locate(project, paths), gate);
  }

  /**
   * Reads each of {@code reports}.
   *
   * @throws CrapReportException if a report cannot be parsed
   */
  List<LineHits> parse(final Project project, final List<Path> reports, final String gate) {
    final List<LineHits> hits = new ArrayList<>();
    for (final Path report : reports) {
      try {
        hits.add(ports.reader().read(report));
      } catch (LineCoverageException e) {
        throw new CrapReportException(
            display(project.root(), report)
                + " cannot be parsed as a JaCoCo XML or lcov report; no score was used."
                + first,
            e);
      }
    }
    LOGGER.log(Level.INFO, () -> "event=crap-reports gate=" + gate + " reports=" + hits.size());
    return hits;
  }

  /**
   * Fails when a source file that a report names was modified after that report was written.
   *
   * @throws CrapReportException naming the report and the source file
   */
  void requireFresh(final Path root, final Map<String, Set<Path>> namedBy) {
    for (final Map.Entry<String, Set<Path>> named : new TreeMap<>(namedBy).entrySet()) {
      final Instant source = ports.files().modified(root.resolve(named.getKey()));
      for (final Path report : named.getValue()) {
        if (source.isAfter(ports.files().modified(report))) {
          throw new CrapReportException(
              display(root, report)
                  + " is older than "
                  + named.getKey()
                  + ", which it names."
                  + first);
        }
      }
    }
  }

  /**
   * Returns the report files of {@code project}: the configured {@code paths}, or the build tool's
   * conventional report in the root and every module.
   *
   * @throws CrapReportException if no report exists
   */
  List<Path> locate(final Project project, final List<String> paths) {
    final Path root = project.root();
    if (!paths.isEmpty()) {
      return paths.stream().map(path -> configured(root, path)).toList();
    }
    final String conventional =
        ConventionalCoverageReports.of(project.buildTool())
            .orElseThrow(
                () ->
                    new CrapReportException(
                        project.buildTool().displayName()
                            + " has no conventional coverage report location; set 'report'."));
    final List<Path> found =
        ports.files().moduleDirectories(root).stream()
            .map(directory -> directory.resolve(conventional))
            .filter(ports.files()::isFile)
            .toList();
    if (found.isEmpty()) {
      throw new CrapReportException("No coverage report " + conventional + " was found." + first);
    }
    return found;
  }

  private Path configured(final Path root, final String path) {
    final Path report = root.resolve(path);
    if (!ports.files().isFile(report)) {
      throw new CrapReportException("The coverage report " + path + " does not exist." + first);
    }
    return report;
  }

  private static String display(final Path root, final Path report) {
    final Path base = root.toAbsolutePath().normalize();
    final Path normal = report.toAbsolutePath().normalize();
    return (normal.startsWith(base) ? base.relativize(normal) : normal)
        .toString()
        .replace('\\', '/');
  }
}
