package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.FileLines;
import dev.ward.domain.gate.LineCoverageException;
import dev.ward.domain.gate.LineCoverageReport;
import dev.ward.domain.gate.LineHits;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Reads the {@code SF:}, {@code DA:}, {@code BRDA:}, {@code BRF:} and {@code end_of_record} lines
 * of an lcov tracefile.
 */
public final class LcovLineCoverage implements LineCoverageReport {
  private static final Logger LOGGER = Logger.getLogger(LcovLineCoverage.class.getName());
  private static final String SOURCE_FILE = "SF:";
  private static final String LINE_DATA = "DA:";
  private static final String BRANCH_DATA = "BRDA:";
  private static final String BRANCH_FOUND = "BRF:";
  private static final String END = "end_of_record";
  private static final String TEST_NAME = "TN:";
  private static final String NO_RECORD = "";
  private static final String NOT_TAKEN = "-";
  private static final int TAKEN = 3;

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
    final Records records = new Records(report);
    lines.forEach(raw -> records.accept(raw.strip()));
    records.finish();
    LOGGER.log(Level.FINE, () -> "event=lcov-read entries=" + records.entries.size());
    return new LineHits(report, records.entries, records.recordsBranches);
  }

  /** Accumulates the record being read and the entries of the records already closed. */
  private static final class Records {
    private final Path report;
    private final List<LineHits.Entry> entries = new ArrayList<>();
    private final SortedSet<Integer> coverable = new TreeSet<>();
    private final SortedSet<Integer> covered = new TreeSet<>();
    private final SortedMap<Integer, Integer> branches = new TreeMap<>();
    private final SortedMap<Integer, Integer> coveredBranches = new TreeMap<>();
    private String name = NO_RECORD;
    private boolean recordsBranches;

    Records(final Path report) {
      this.report = report;
    }

    void accept(final String line) {
      if (line.startsWith(SOURCE_FILE)) {
        name = line.substring(SOURCE_FILE.length());
      } else if (line.startsWith(LINE_DATA)) {
        requireRecord();
        parseLine(line.substring(LINE_DATA.length()));
      } else if (line.startsWith(BRANCH_DATA)) {
        requireRecord();
        recordsBranches = true;
        parseBranch(line.substring(BRANCH_DATA.length()));
      } else if (line.startsWith(BRANCH_FOUND)) {
        requireRecord();
        recordsBranches = true;
      } else if (END.equals(line)) {
        close();
      }
    }

    /** Fails when the file ends inside a record, counting no part of it. */
    void finish() {
      if (!NO_RECORD.equals(name)) {
        throw new LineCoverageException(report.getFileName() + " is truncated: no end_of_record");
      }
    }

    private void close() {
      requireRecord();
      entries.add(
          new LineHits.Entry(
              LineHits.Kind.PATH,
              name,
              new FileLines(coverable, covered, branches, coveredBranches)));
      name = NO_RECORD;
      coverable.clear();
      covered.clear();
      branches.clear();
      coveredBranches.clear();
    }

    private void requireRecord() {
      if (NO_RECORD.equals(name)) {
        throw new LineCoverageException(
            report.getFileName() + " is not an lcov report: a record has no SF line");
      }
    }

    private void parseLine(final String data) {
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

    /** A {@code BRDA:line,block,branch,taken} record; {@code -} taken means never evaluated. */
    private void parseBranch(final String data) {
      final String[] parts = data.split(",", -1);
      try {
        final int number = Integer.parseInt(parts[0].strip());
        final String taken = parts[TAKEN].strip();
        branches.merge(number, 1, Integer::sum);
        if (!NOT_TAKEN.equals(taken) && Math.round(Double.parseDouble(taken)) > 0) {
          coveredBranches.merge(number, 1, Integer::sum);
        }
      } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
        throw new LineCoverageException(
            report.getFileName() + " is not an lcov report: a BRDA line is malformed", e);
      }
    }
  }
}
