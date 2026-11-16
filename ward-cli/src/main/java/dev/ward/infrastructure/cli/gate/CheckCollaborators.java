package dev.ward.infrastructure.cli.gate;

import dev.ward.application.gate.CheckReportExport;
import dev.ward.application.learning.LearningService;
import dev.ward.application.vulnerability.VulnerabilityRunMode;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * What {@code ward check} uses around a run: learning, report export, the network mode and the
 * number of processors.
 */
final class CheckCollaborators {
  private final LearningService learning;
  private final CheckReportExport export;
  private final VulnerabilityRunMode runMode;
  private final int processors;

  @Inject
  CheckCollaborators(
      final LearningService learning,
      final CheckReportExport export,
      final VulnerabilityRunMode runMode,
      @Named("ward.processors") final int processors
  ) {
    this.learning = learning;
    this.export = export;
    this.runMode = runMode;
    this.processors = processors;
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

  /** The number of processors, which {@code --jobs auto} uses. */
  int processors() {
    return processors;
  }
}
