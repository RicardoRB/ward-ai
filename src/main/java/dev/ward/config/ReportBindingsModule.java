package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.application.gate.GateReportPorts;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.infrastructure.finding.FileSystemReportDirectories;
import dev.ward.infrastructure.finding.PitXmlMutationReport;
import dev.ward.infrastructure.finding.StrykerJsonMutationReport;

@Module
final class ReportBindingsModule {
  private ReportBindingsModule() {}

  @Provides
  static ReportDirectories reportDirectories(final FileSystemReportDirectories implementation) {
    return implementation;
  }

  @Provides
  static GateReportPorts gateReportPorts(
      final ReportDirectories directories,
      final StrykerJsonMutationReport stryker,
      final PitXmlMutationReport pit) {
    return new GateReportPorts(directories, stryker, pit);
  }
}
