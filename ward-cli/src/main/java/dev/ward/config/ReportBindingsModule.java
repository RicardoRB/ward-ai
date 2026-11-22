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
import dev.ward.infrastructure.finding.dotnet.CoberturaXmlLineCoverage;
import dev.ward.infrastructure.finding.dotnet.StrykerNetMutationReport;
import dev.ward.infrastructure.finding.jvm.AndroidJacocoCoverageReport;
import dev.ward.infrastructure.finding.jvm.JacocoXmlLineCoverage;
import dev.ward.infrastructure.finding.jvm.PitXmlMutationReport;
import dev.ward.infrastructure.finding.node.StrykerJsonMutationReport;
import dev.ward.infrastructure.finding.python.MutmutMetaMutationReport;
import dev.ward.infrastructure.finding.rust.CargoMutantsReport;
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
      final CoberturaXmlLineCoverage cobertura,
      final JacocoXmlLineCoverage jacoco,
      final LcovLineCoverage lcov
  ) {
    return new DetectedLineCoverage(List.of(cobertura, jacoco, lcov));
  }

  /** The mutation readers, grouped so the report ports take few parameters. */
  record MutationReports(
      StrykerJsonMutationReport stryker,
      PitXmlMutationReport pit,
      MutmutMetaMutationReport mutmut,
      StrykerNetMutationReport strykerNet
  ) {}

  @Provides
  static MutationReports mutationReports(
      final StrykerJsonMutationReport stryker,
      final PitXmlMutationReport pit,
      final MutmutMetaMutationReport mutmut,
      final StrykerNetMutationReport strykerNet
  ) {
    return new MutationReports(stryker, pit, mutmut, strykerNet);
  }

  @Provides
  static GateReportPorts gateReportPorts(
      final ReportDirectories directories,
      final MutationReports mutation,
      final PlatformGatePorts swift
  ) {
    return new GateReportPorts(
        directories,
        mutation.stryker(),
        mutation.pit(),
        mutation.mutmut(),
        swift,
        mutation.strykerNet()
    );
  }

  @Provides
  static PlatformGatePorts platformGatePorts(
      final SimctlSimulators simulators,
      final LlvmCovCoverageReport llvmCov,
      final XccovCoverageReport xccov,
      final AndroidJacocoCoverageReport android,
      final CargoMutantsReport cargoMutants
  ) {
    return new PlatformGatePorts(simulators, llvmCov, xccov, android, cargoMutants);
  }
}
