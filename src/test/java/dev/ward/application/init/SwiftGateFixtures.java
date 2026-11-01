package dev.ward.application.init;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import java.util.List;
import java.util.Optional;

/** Builds the Swift tools that the Swift gate tests run on. */
final class SwiftGateFixtures {
  private static final List<String> SOURCES = List.of("Package.swift", "Sources", "Tests");

  private SwiftGateFixtures() {}

  /** Returns SwiftPM tools for {@code preset}, with the user's own SwiftLint config or none. */
  static SwiftTools swiftPm(final QualityPreset preset, final boolean ownSwiftLintConfig) {
    return new SwiftTools(
        BuildTool.SWIFTPM, true, preset, SOURCES, Optional.empty(), null, null, ownSwiftLintConfig);
  }

  /** Returns Xcode tools for the standard preset on {@code platform}. */
  static SwiftTools xcode(final String platform, final Optional<String> workspace) {
    return xcode(QualityPreset.STANDARD, platform, workspace);
  }

  /** Returns Xcode tools for {@code preset} on {@code platform}, in {@code workspace} if any. */
  static SwiftTools xcode(
      final QualityPreset preset, final String platform, final Optional<String> workspace) {
    return new SwiftTools(
        BuildTool.XCODE, true, preset, SOURCES, workspace, "Shop", platform, false);
  }
}
