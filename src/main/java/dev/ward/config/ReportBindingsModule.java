package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.application.gate.GateReportPorts;
import dev.ward.application.gate.PlatformGatePorts;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.infrastructure.finding.AndroidJacocoCoverageReport;
import dev.ward.infrastructure.finding.FileSystemReportDirectories;
import dev.ward.infrastructure.finding.LlvmCovCoverageReport;
import dev.ward.infrastructure.finding.MutmutMetaMutationReport;
import dev.ward.infrastructure.finding.PitXmlMutationReport;
import dev.ward.infrastructure.finding.StrykerJsonMutationReport;
import dev.ward.infrastructure.finding.XccovCoverageReport;
import dev.ward.infrastructure.gate.SimctlSimulators;

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
      final PitXmlMutationReport pit,
      final MutmutMetaMutationReport mutmut,
      final PlatformGatePorts swift) {
    return new GateReportPorts(directories, stryker, pit, mutmut, swift);
  }

  @Provides
  static PlatformGatePorts platformGatePorts(
      final SimctlSimulators simulators,
      final LlvmCovCoverageReport llvmCov,
      final XccovCoverageReport xccov,
      final AndroidJacocoCoverageReport android) {
    return new PlatformGatePorts(simulators, llvmCov, xccov, android);
  }
}
