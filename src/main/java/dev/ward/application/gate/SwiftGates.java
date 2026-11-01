package dev.ward.application.gate;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.config.XcodePlatform;
import dev.ward.domain.gate.CoverageReport;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.process.CommandExecutor;

/** Builds the Swift gates that do more than run their command: Xcode tests and coverage. */
final class SwiftGates {
  private static final String XCCOV = "xccov";

  private SwiftGates() {}

  /** Builds an {@code xcodebuild-test} or {@code swift-coverage} gate for {@code check}. */
  static QualityGate create(
      final GateConfiguration check,
      final CommandExecutor executor,
      final GateReportPorts reports) {
    return SupportedQualityGates.XCODEBUILD_TEST.equals(check.type())
        ? xcodeTestGate(check, executor, reports)
        : coverageGate(check, executor, reports);
  }

  private static QualityGate xcodeTestGate(
      final GateConfiguration check,
      final CommandExecutor executor,
      final GateReportPorts reports) {
    if (!check.command().contains(XcodeTestGate.DESTINATION)
        && (check.platform() == null
            || check.platform() == XcodePlatform.IOS_SIMULATOR
                && reports.swift().simulators() == null)) {
      throw new WardException(
          "The '"
              + check.name()
              + "' check needs a platform, one of "
              + XcodePlatform.labels()
              + ", or a -destination in its command.");
    }
    return new XcodeTestGate(check.name(), executor, check.command(), check.platform(), reports);
  }

  /** Reads {@code xccov}'s report when the command runs it, and the llvm-cov export otherwise. */
  private static QualityGate coverageGate(
      final GateConfiguration check,
      final CommandExecutor executor,
      final GateReportPorts reports) {
    final CoverageReport report =
        check.command().stream().anyMatch(token -> token.endsWith(XCCOV))
            ? reports.swift().xccov()
            : reports.swift().llvmCov();
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
