package dev.ward.infrastructure.init.swift;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.SwiftSupport;
import dev.ward.domain.project.SwiftToolchainInspection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The blocking findings for a Swift project: a machine that is not macOS, an Xcode older than 26.0
 * or unreadable, and an Xcode project with a {@code Podfile}. Each names what to change.
 */
final class SwiftCompatibility {
  private static final String PODFILE = "Podfile";

  private SwiftCompatibility() {}

  /** Returns the problems that stop init before it writes anything; empty when it may proceed. */
  static List<String> problems(final Project project, final SwiftToolchainInspection toolchain) {
    final List<String> problems = new ArrayList<>();
    if (!toolchain.macOs()) {
      problems.add(
          "Ward sets up Swift projects on macOS only; run ward init on a Mac with Xcode 26.0 or"
              + " newer.");
      return List.copyOf(problems);
    }
    xcodeProblem(toolchain).ifPresent(problems::add);
    if (project.buildTool() == BuildTool.XCODE
        && Files.isRegularFile(project.root().resolve(PODFILE))) {
      problems.add(
          "Ward does not set up CocoaPods projects; move the dependencies to Swift Package Manager"
              + " to use Ward with this project.");
    }
    return List.copyOf(problems);
  }

  private static Optional<String> xcodeProblem(final SwiftToolchainInspection toolchain) {
    final Optional<String> version = toolchain.xcodeVersion();
    if (version.isEmpty()) {
      return Optional.of(
          "Ward cannot read the selected Xcode version; select Xcode "
              + SwiftSupport.MINIMUM_XCODE_MAJOR
              + "."
              + SwiftSupport.MINIMUM_XCODE_MINOR
              + " or newer with xcode-select.");
    }
    return SwiftSupport.xcodeIncompatibility(version.get());
  }
}
