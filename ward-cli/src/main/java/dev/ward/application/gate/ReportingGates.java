package dev.ward.application.gate;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.process.CommandExecutor;
import java.util.List;

/** Builds the in-process gates that read a report or the dependency inventory themselves. */
final class ReportingGates {
  private ReportingGates() {}

  /**
   * Builds the {@code secrets}, {@code coverage}, {@code crap} or {@code vulnerabilities} gate of
   * {@code check}.
   *
   * @throws WardException if the dependencies of the gate are unavailable
   */
  static QualityGate create(
      final GateConfiguration check,
      final List<String> focus,
      final CommandExecutor executor,
      final GateReportPorts reports,
      final GateExtensions extensions) {
    if (SupportedQualityGates.SECRETS.equals(check.type())) {
      if (extensions.secrets() == null) {
        throw new WardException("Secrets gate dependencies are unavailable.");
      }
      return extensions.secrets().create(check, focus);
    }
    if (SupportedQualityGates.COVERAGE.equals(check.type())) {
      return CoverageGates.report(check, executor, reports, extensions);
    }
    if (SupportedQualityGates.CRAP.equals(check.type())) {
      return CrapGates.create(check, extensions);
    }
    return VulnerabilityGates.from(extensions, check);
  }
}
