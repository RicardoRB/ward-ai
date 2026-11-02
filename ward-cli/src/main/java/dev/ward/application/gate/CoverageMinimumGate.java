package dev.ward.application.gate;

import dev.ward.domain.gate.CoverageReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.LineCoverage;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs a coverage command, then fails when the line coverage it reports is below the minimum. */
final class CoverageMinimumGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(CoverageMinimumGate.class.getName());
  private final QualityGate command;
  private final CoverageReport report;
  private final int minimum;

  CoverageMinimumGate(final QualityGate command, final CoverageReport report, final int minimum) {
    this.command = command;
    this.report = report;
    this.minimum = minimum;
  }

  @Override
  public String name() {
    return command.name();
  }

  @Override
  public GateResult execute(final Project project) {
    final GateResult result = command.execute(project);
    if (result.status() != GateStatus.PASSED) {
      return result;
    }
    final LineCoverage coverage = report.read(project, result.stdout());
    if (!coverage.usable()) {
      LOGGER.log(Level.INFO, () -> "event=coverage-report-unusable gate=" + name());
      return with(result, GateStatus.EXECUTION_ERROR, coverage.problem());
    }
    LOGGER.log(
        Level.INFO,
        () ->
            "event=coverage-lines gate="
                + name()
                + " covered="
                + coverage.covered()
                + " executable="
                + coverage.executable()
                + " minimum="
                + minimum);
    if (coverage.below(minimum)) {
      return with(
          result,
          GateStatus.FAILED,
          "Line coverage " + coverage.percent() + "% is below the minimum of " + minimum + "%.");
    }
    return with(
        result,
        GateStatus.PASSED,
        "Line coverage " + coverage.percent() + "% meets the minimum of " + minimum + "%.");
  }

  private static GateResult with(
      final GateResult result, final GateStatus status, final String summary) {
    return GateResult.builder(result.name(), status)
        .command(result.command())
        .exitCode(result.exitCode())
        .duration(result.duration())
        .stdout(result.stdout())
        .stderr(result.stderr())
        .summary(summary)
        .build();
  }
}
