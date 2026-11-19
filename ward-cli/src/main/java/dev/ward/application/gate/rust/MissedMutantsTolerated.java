package dev.ward.application.gate.rust;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;

/**
 * Runs {@code cargo mutants} and counts its two result exit codes as a finished run: 2 when some
 * mutants were missed and 3 when some timed out. The score gate then reads the result files and
 * decides; any other non-zero exit code, such as a failing baseline test run, still fails.
 */
public final class MissedMutantsTolerated implements QualityGate {
  private static final int MISSED = 2;
  private static final int TIMED_OUT = 3;

  private final QualityGate command;

  /** Wraps the gate that runs {@code cargo mutants}. */
  public MissedMutantsTolerated(final QualityGate command) {
    this.command = command;
  }

  @Override
  public String name() {
    return command.name();
  }

  @Override
  public GateResult execute(final Project project) {
    final GateResult result = command.execute(project);
    final boolean finished = result.exitCode() == MISSED || result.exitCode() == TIMED_OUT;
    if (result.status() != GateStatus.FAILED || !finished) {
      return result;
    }
    return GateResult.builder(result.name(), GateStatus.PASSED)
        .command(result.command())
        .exitCode(result.exitCode())
        .duration(result.duration())
        .stdout(result.stdout())
        .stderr(result.stderr())
        .summary(result.summary())
        .build();
  }
}
