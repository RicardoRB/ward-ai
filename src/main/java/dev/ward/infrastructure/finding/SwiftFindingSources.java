package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.SupportedQualityGates;
import java.util.List;
import javax.inject.Inject;

/** The finding sources of the Swift toolchain's gates. */
public final class SwiftFindingSources {
  private final List<GateFindingSource> sources;

  /** Groups the Swift finding sources; compiler errors come from both build and Xcode tests. */
  @Inject
  public SwiftFindingSources(
      final SwiftLintJsonFindings swiftLint, final SwiftFormatOutputFindings swiftFormat) {
    this.sources =
        List.of(
            swiftLint,
            swiftFormat,
            new SwiftCompilerOutputFindings("swift-build"),
            new SwiftCompilerOutputFindings(SupportedQualityGates.XCODEBUILD_TEST));
  }

  List<GateFindingSource> sources() {
    return sources;
  }
}
