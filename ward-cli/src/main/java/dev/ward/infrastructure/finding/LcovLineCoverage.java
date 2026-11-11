package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineCoverageReport;
import dev.ward.domain.gate.LineHits;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads the {@code SF:}, {@code DA:} and {@code end_of_record} lines of an lcov tracefile. */
public final class LcovLineCoverage implements LineCoverageReport {
  private static final Logger LOGGER = Logger.getLogger(LcovLineCoverage.class.getName());
  private static final String SOURCE_FILE = "SF:";
  private static final String LINE_DATA = "DA:";
  private static final String END = "end_of_record";
  private static final String TEST_NAME = "TN:";

  /** Creates the stateless reader that Dagger injects. */
  @Inject
  public LcovLineCoverage() {}

  @Override
  public boolean accepts(final String head) {
    final String start = head.stripLeading();
    return start.startsWith(TEST_NAME) || start.startsWith(SOURCE_FILE);
  }

  @Override
  public LineHits read(final Path report) {
    final List<String> lines;
    try {
      lines = Files.readAllLines(report, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new LineCoverageException("Cannot read " + report.getFileName(), e);
    }
    final List<LineHits.Entry> entries = new ArrayList<>();
    String name = null;
    final TreeSet<Integer> coverable = new TreeSet<>();
    final TreeSet<Integer> covered = new TreeSet<>();
    for (final String raw : lines) {
      final String line = raw.strip();
      if (line.startsWith(SOURCE_FILE)) {
        name = line.substring(SOURCE_FILE.length());
      } else if (line.startsWith(LINE_DATA)) {
        requireRecord(name, report);
        parseLine(line.substring(LINE_DATA.length()), coverable, covered, report);
      } else if (END.equals(line)) {
        requireRecord(name, report);
        entries.add(
            new LineHits.Entry(LineHits.Kind.PATH, name, new FileLines(coverable, covered)));
        name = null;
        coverable.clear();
        covered.clear();
      }
    }
    if (name != null) {
      throw new LineCoverageException(report.getFileName() + " is truncated: no end_of_record");
    }
    LOGGER.log(Level.FINE, () -> "event=lcov-read entries=" + entries.size());
    return new LineHits(report, entries);
  }

  private static void requireRecord(final String name, final Path report) {
    if (name == null) {
      throw new LineCoverageException(
          report.getFileName() + " is not an lcov report: a record has no SF line");
    }
  }

  private static void parseLine(
      final String data,
      final TreeSet<Integer> coverable,
      final TreeSet<Integer> covered,
      final Path report) {
    final String[] parts = data.split(",", -1);
    try {
      final int number = Integer.parseInt(parts[0].strip());
      final long hits = Math.round(Double.parseDouble(parts[1].strip()));
      coverable.add(number);
      if (hits > 0) {
        covered.add(number);
      }
    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
      throw new LineCoverageException(
          report.getFileName() + " is not an lcov report: a DA line is malformed", e);
    }
  }
}
