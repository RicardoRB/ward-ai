package dev.ward.application.gate;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.MutationCounts;
import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs Stryker, then fails when the mutation score in its JSON report is below the minimum. */
final class StrykerGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(StrykerGate.class.getName());
  private static final String REPORTER_HINT =
      " Enable Stryker's json reporter in the command, for example with"
          + " --reporters clear-text,progress,json.";

  private final QualityGate command;
  private final MutationReport report;
  private final int minimum;

  StrykerGate(final QualityGate command, final MutationReport report, final int minimum) {
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
    final Instant start = Instant.now();
    final GateResult result = command.execute(project);
    if (result.status() != GateStatus.PASSED) {
      return result;
    }
    final MutationCounts counts = report.read(project, start);
    if (!counts.usable()) {
      LOGGER.log(Level.INFO, "event=stryker-report-unusable reason=unreadable");
      return with(result, GateStatus.EXECUTION_ERROR, counts.problem() + REPORTER_HINT);
    }
    if (counts.valid() == 0) {
      LOGGER.log(Level.INFO, "event=stryker-report-unusable reason=no-valid-mutants");
      return with(
          result,
          GateStatus.EXECUTION_ERROR,
          "Stryker's JSON report has no valid mutants, so no mutation score can be computed."
              + REPORTER_HINT);
    }
    LOGGER.log(
        Level.INFO,
        () ->
            "event=stryker-score detected="
                + counts.detected()
                + " undetected="
                + counts.undetected()
                + " score="
                + counts.score()
                + " minimum="
                + minimum);
    if (counts.below(minimum)) {
      return with(
          result,
          GateStatus.FAILED,
          "Mutation score " + counts.score() + "% is below the minimum of " + minimum + "%.");
    }
    return with(
        result,
        GateStatus.PASSED,
        "Mutation score " + counts.score() + "% meets the minimum of " + minimum + "%.");
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
