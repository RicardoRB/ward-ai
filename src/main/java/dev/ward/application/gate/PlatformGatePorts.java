package dev.ward.application.gate;

import dev.ward.domain.gate.CoverageReport;
import dev.ward.domain.gate.Simulators;

/**
 * The ports the platform gates use: the Swift gates on Apple platforms and the Android coverage
 * gate.
 *
 * @param simulators boots the simulator Xcode tests run on, or {@code null} when unavailable
 * @param llvmCov reads the llvm-cov export of {@code swift test}, or {@code null} when unavailable
 * @param xccov reads {@code xccov}'s JSON report, or {@code null} when it is unavailable
 * @param android reads AGP's JaCoCo XML report for the debug unit tests, or {@code null} when it is
 *     unavailable
 */
public record PlatformGatePorts(
    Simulators simulators, CoverageReport llvmCov, CoverageReport xccov, CoverageReport android) {
  /** No platform ports, for factories that do not run platform gates. */
  public static final PlatformGatePorts NONE = new PlatformGatePorts(null, null, null, null);

  /** Creates the Swift ports, without the Android coverage report. */
  public PlatformGatePorts(
      final Simulators simulators, final CoverageReport llvmCov, final CoverageReport xccov) {
    this(simulators, llvmCov, xccov, null);
  }
}
