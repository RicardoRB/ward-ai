package dev.ward.application.gate;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.domain.config.CoverageOptions;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.gate.LineHits;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The {@code coverage} gate: runs its command when it has one, reads the JaCoCo XML or lcov
 * reports, sums them over every module and fails when the line or branch coverage is below its
 * minimum.
 */
final class CoverageReportGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(CoverageReportGate.class.getName());

  private final String name;
  private final QualityGate command;
  private final int minimum;
  private final CoverageOptions options;
  private final CrapPorts ports;
  private final AnalysisPorts analysis;
  private final CrapReports reports;

  /**
   * Creates the gate of {@code check}, whose {@code minimum} and coverage options it enforces.
   * {@code command} is {@code null} when the check only reads an earlier report; {@code analysis}
   * then finds the sources a report must not be older than.
   */
  CoverageReportGate(
      final GateConfiguration check,
      final QualityGate command,
      final CrapPorts ports,
      final AnalysisPorts analysis) {
    this.name = check.name();
    this.command = command;
    this.minimum = check.minimum();
    this.options = check.coverage();
    this.ports = ports;
    this.analysis = analysis;
    this.reports = new CrapReports(ports, CrapReports.TESTS_FIRST);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    final Instant start = Instant.now();
    GateResult ran = null;
    if (command != null) {
      ran = command.execute(project);
      if (ran.status() != GateStatus.PASSED) {
        return ran;
      }
    }
    try {
      return check(project, ran, start);
    } catch (CrapReportException e) {
      LOGGER.log(Level.INFO, () -> "event=coverage-unusable gate=" + name);
      return with(ran, GateStatus.EXECUTION_ERROR, e.getMessage());
    }
  }

  private GateResult check(final Project project, final GateResult ran, final Instant start) {
    final List<Path> files = reports.locate(project, options.reports());
    final List<Path> fresh = command == null ? files : writtenSince(files, start);
    final List<LineHits> hits = reports.parse(project, fresh, name);
    if (command == null) {
      requireNewerThanSources(project, hits);
    }
    final CoverageTotals totals = CoverageTotals.of(project.root(), hits, ports);
    if (totals.lines().executable() == 0) {
      throw new CrapReportException(
          "The coverage reports have no coverable line." + CrapReports.TESTS_FIRST);
    }
    final Integer branchMinimum = options.minimumBranches();
    if (branchMinimum != null && hits.stream().noneMatch(LineHits::recordsBranches)) {
      throw new CrapReportException(
          "The coverage reports record no branches, so the branch minimum cannot be checked."
              + " The tool did not measure branches; enable branch coverage in it."
              + " coverage.py also writes no branch record for code that has no branch.");
    }
    return compare(ran, totals, branchMinimum);
  }

  /** Keeps the reports written after the command started, at the file system's second grain. */
  private List<Path> writtenSince(final List<Path> files, final Instant start) {
    final Instant earliest = start.truncatedTo(ChronoUnit.SECONDS);
    final List<Path> fresh =
        files.stream().filter(file -> !ports.files().modified(file).isBefore(earliest)).toList();
    if (fresh.isEmpty()) {
      throw new CrapReportException(
          "No coverage report was written after the command started; the reports that exist are left"
              + " from an earlier run.");
    }
    return fresh;
  }

  private void requireNewerThanSources(final Project project, final List<LineHits> hits) {
    final List<SourceUnit> units =
        analysis.sources().read(project.root(), EnumSet.allOf(AnalysisLanguage.class));
    final CoverageMatching matching =
        CoverageMatching.of(
            project.root(),
            hits,
            units.stream().map(SourceUnit::path).collect(Collectors.toUnmodifiableSet()));
    reports.requireFresh(project.root(), matching.namedBy());
  }

  private GateResult compare(
      final GateResult ran, final CoverageTotals totals, final Integer branchMin) {
    LOGGER.log(
        Level.INFO,
        () ->
            "event=coverage-totals gate="
                + name
                + " lines="
                + totals.lines().covered()
                + "/"
                + totals.lines().executable()
                + " branches="
                + totals.branches().covered()
                + "/"
                + totals.branches().executable());
    final boolean linesBelow = totals.lines().below(minimum);
    final boolean branchesBelow = branchMin != null && totals.branches().below(branchMin);
    final StringBuilder summary =
        new StringBuilder(describe("Line", totals.lines(), minimum, linesBelow));
    if (branchMin != null) {
      summary.append(' ').append(describe("Branch", totals.branches(), branchMin, branchesBelow));
    }
    final boolean failed = linesBelow || branchesBelow;
    return with(ran, failed ? GateStatus.FAILED : GateStatus.PASSED, summary.toString());
  }

  private static String describe(
      final String kind, final LineCoverage coverage, final int limit, final boolean below) {
    return kind
        + " coverage "
        + coverage.percent()
        + "% "
        + (below ? "is below" : "meets")
        + " the minimum of "
        + limit
        + "%.";
  }

  private GateResult with(final GateResult ran, final GateStatus status, final String summary) {
    final GateResult.Builder builder = GateResult.builder(name, status).summary(summary);
    if (ran != null) {
      builder
          .command(ran.command())
          .exitCode(ran.exitCode())
          .duration(ran.duration())
          .stdout(ran.stdout())
          .stderr(ran.stderr());
    }
    return builder.build();
  }
}
