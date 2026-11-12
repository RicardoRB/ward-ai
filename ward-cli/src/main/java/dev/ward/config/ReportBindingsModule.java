package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.application.gate.GateReportPorts;
import dev.ward.application.gate.PlatformGatePorts;
import dev.ward.domain.gate.LineCoverageReport;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.infrastructure.finding.DetectedLineCoverage;
import dev.ward.infrastructure.finding.FileSystemReportDirectories;
import dev.ward.infrastructure.finding.LcovLineCoverage;
import dev.ward.infrastructure.finding.jvm.AndroidJacocoCoverageReport;
import dev.ward.infrastructure.finding.jvm.JacocoXmlLineCoverage;
import dev.ward.infrastructure.finding.jvm.PitXmlMutationReport;
import dev.ward.infrastructure.finding.node.StrykerJsonMutationReport;
import dev.ward.infrastructure.finding.python.MutmutMetaMutationReport;
import dev.ward.infrastructure.finding.swift.LlvmCovCoverageReport;
import dev.ward.infrastructure.finding.swift.XccovCoverageReport;
import dev.ward.infrastructure.gate.swift.SimctlSimulators;
import java.util.List;

@Module
final class ReportBindingsModule {
  private ReportBindingsModule() {}

  @Provides
  static ReportDirectories reportDirectories(final FileSystemReportDirectories implementation) {
    return implementation;
  }

  @Provides
  static LineCoverageReport lineCoverageReport(
      final JacocoXmlLineCoverage jacoco, final LcovLineCoverage lcov) {
    return new DetectedLineCoverage(List.of(jacoco, lcov));
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
