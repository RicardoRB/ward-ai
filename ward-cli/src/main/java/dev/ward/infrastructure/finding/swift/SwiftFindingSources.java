package dev.ward.infrastructure.finding.swift;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.infrastructure.finding.GateFindingSource;
import java.util.List;
import javax.inject.Inject;

/** The finding sources of the Swift toolchain's gates. */
public final class SwiftFindingSources {
  private final List<GateFindingSource> sources;

  /** Groups the Swift finding sources; compiler errors come from both build and Xcode tests. */
  @Inject
  public SwiftFindingSources(final SwiftLintJsonFindings swiftLint) {
    this.sources =
        List.of(
            swiftLint,
            new SwiftCompilerOutputFindings("swift-build"),
            new SwiftCompilerOutputFindings(SupportedQualityGates.XCODEBUILD_TEST));
  }

  /** Returns the finding sources of the Swift gates. */
  public List<GateFindingSource> sources() {
    return sources;
  }
}
