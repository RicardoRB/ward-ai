package dev.ward.infrastructure.cli.gate;

import dev.ward.application.gate.CheckReportExport;
import dev.ward.application.learning.LearningService;
import dev.ward.application.vulnerability.VulnerabilityRunMode;
import javax.inject.Inject;

/** What {@code ward check} uses around a run: learning, report export and the network mode. */
final class CheckCollaborators {
  private final LearningService learning;
  private final CheckReportExport export;
  private final VulnerabilityRunMode runMode;

  @Inject
  CheckCollaborators(
      final LearningService learning,
      final CheckReportExport export,
      final VulnerabilityRunMode runMode) {
    this.learning = learning;
    this.export = export;
    this.runMode = runMode;
  }

  LearningService learning() {
    return learning;
  }

  CheckReportExport export() {
    return export;
  }

  VulnerabilityRunMode runMode() {
    return runMode;
  }
}
