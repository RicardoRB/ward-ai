package dev.ward.application.gate;

import dev.ward.domain.gate.MutationReport;
import dev.ward.domain.gate.ReportDirectories;

/**
 * The ports gates use for the reports their tools write and the simulators they run on.
 *
 * @param directories creates a report's parent directory before a command runs
 * @param stryker reads Stryker's mutation report, or {@code null} when it is unavailable
 * @param pit reads PIT's mutation report, or {@code null} when it is unavailable
 * @param mutmut reads mutmut's per-file results, or {@code null} when it is unavailable
 * @param swift the Swift simulator and coverage ports
 */
public record GateReportPorts(
    ReportDirectories directories,
    MutationReport stryker,
    MutationReport pit,
    MutationReport mutmut,
    PlatformGatePorts swift) {

  /** Creates the ports without the Swift ones. */
  public GateReportPorts(
      final ReportDirectories directories,
      final MutationReport stryker,
      final MutationReport pit,
      final MutationReport mutmut) {
    this(directories, stryker, pit, mutmut, PlatformGatePorts.NONE);
  }
}
