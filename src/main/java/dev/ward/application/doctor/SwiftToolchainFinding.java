package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.SwiftSupport;
import dev.ward.domain.project.SwiftToolchainInspection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Checks the Swift tool chain, the selected Xcode, and setups Ward cannot configure yet. */
final class SwiftToolchainFinding implements ToolchainFinding {

  @Override
  public List<Finding> findings(
      final Project detected, final EnvironmentFacts facts, final ToolchainContext context) {
    final List<Finding> findings = new ArrayList<>();
    findings.add(executableFinding("swift", facts));
    if (detected.buildTool() != BuildTool.SWIFTPM) {
      findings.add(executableFinding("xcodebuild", facts));
      findings.add(executableFinding("xcrun", facts));
    }
    context.swift().map(SwiftToolchainFinding::xcodeFinding).ifPresent(findings::add);
    unsupportedFinding(detected).ifPresent(findings::add);
    return findings;
  }

  private static Finding executableFinding(final String executable, final EnvironmentFacts facts) {
    return facts.hasExecutable(executable)
        ? new Finding(executable, Status.OK, executable + " available.")
        : new Finding(executable, Status.ERROR, executable + " is required on PATH.");
  }

  private static Finding xcodeFinding(final SwiftToolchainInspection swift) {
    if (!swift.macOs()) {
      return new Finding("platform", Status.WARNING, "Ward verifies Swift projects on macOS only.");
    }
    return swift
        .xcodeVersion()
        .map(SwiftToolchainFinding::versionFinding)
        .orElseGet(() -> new Finding("xcode", Status.WARNING, "Could not read the Xcode version."));
  }

  private static Finding versionFinding(final String version) {
    return SwiftSupport.xcodeIncompatibility(version)
        .map(message -> new Finding("xcode", Status.ERROR, message))
        .orElseGet(() -> new Finding("xcode", Status.OK, "Xcode " + version + " selected."));
  }

  private static Optional<Finding> unsupportedFinding(final Project detected) {
    return switch (detected.buildTool()) {
      case TUIST, XCODEGEN -> Optional.of(generatorFinding(detected.buildTool()));
      case XCODE ->
          detected.wrapperAvailable() ? Optional.of(cocoaPodsFinding()) : Optional.empty();
      default -> Optional.empty();
    };
  }

  private static Finding generatorFinding(final BuildTool tool) {
    return new Finding(
        tool.displayName().toLowerCase(Locale.ROOT),
        Status.WARNING,
        "Ward does not support " + tool.displayName() + " yet.");
  }

  private static Finding cocoaPodsFinding() {
    return new Finding(
        "cocoapods", Status.WARNING, "CocoaPods trunk is read-only; Ward does not install pods.");
  }
}
