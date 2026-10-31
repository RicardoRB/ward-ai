package dev.ward.application.gate;

import dev.ward.domain.gate.CoverageReport;
import dev.ward.domain.gate.Simulators;

/**
 * The ports the Swift gates use.
 *
 * @param simulators boots the simulator Xcode tests run on, or {@code null} when unavailable
 * @param llvmCov reads the llvm-cov export of {@code swift test}, or {@code null} when unavailable
 * @param xccov reads {@code xccov}'s JSON report, or {@code null} when it is unavailable
 */
public record SwiftGatePorts(Simulators simulators, CoverageReport llvmCov, CoverageReport xccov) {
  /** No Swift ports, for factories that do not run Swift gates. */
  public static final SwiftGatePorts NONE = new SwiftGatePorts(null, null, null);
}
