package dev.ward.application.gate;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.CoverageReport;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.process.CommandExecutor;

/** Builds a coverage gate that runs its command, then checks the minimum of a report it reads. */
final class CoverageGates {
  private CoverageGates() {}

  /** Returns the command's gate wrapped to fail below the check's minimum in {@code report}. */
  static QualityGate minimum(
      final GateConfiguration check,
      final CommandExecutor executor,
      final GateReportPorts reports,
      final CoverageReport report) {
    if (check.minimum() == null || report == null) {
      throw new WardException(
          "The '" + check.name() + "' check needs a minimum line coverage to check.");
    }
    return new CoverageMinimumGate(
        new CommandQualityGate(check.name(), executor, check.command(), reports.directories()),
        report,
        check.minimum());
  }
}
