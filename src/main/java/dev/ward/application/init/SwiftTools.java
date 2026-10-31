package dev.ward.application.init;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gate choices that run a Swift tool for a SwiftPM package or an Xcode project. Xcode projects name
 * a scheme and a platform; SwiftPM packages have neither.
 *
 * @param buildTool {@link BuildTool#SWIFTPM} or {@link BuildTool#XCODE}
 * @param available whether the build tool's executable is on the path
 * @param preset the selected preset, or {@code null} for a custom selection
 * @param sourceDirectories the formatter's inputs: {@code Package.swift} and the source directories
 * @param workspace the {@code .xcworkspace} the Xcode test command uses, when there is one
 * @param scheme the Xcode scheme the test command runs, or {@code null} for SwiftPM
 * @param platform the {@code xcodebuild} platform, or {@code null} for SwiftPM
 */
record SwiftTools(
    BuildTool buildTool,
    boolean available,
    QualityPreset preset,
    List<String> sourceDirectories,
    Optional<String> workspace,
    String scheme,
    String platform) {
  /**
   * The SwiftLint executable that {@code swift package resolve} downloads for the Ward tools
   * package. SwiftPM cannot run a dependency's executable product with {@code swift run}, so the
   * gate runs the artifact directly. The path is SwiftPM's artifact layout for the pinned
   * SwiftLintPlugins.
   */
  static final String SWIFTLINT_BINARY =
      ".ward/swift-tools/.build/artifacts/swiftlintplugins/SwiftLintBinary/"
          + "SwiftLintBinary.artifactbundle/macos/swiftlint";

  static final String XCRESULT = ".ward/reports/xcode/tests.xcresult";
  static final String SWIFT_FORMAT = "swift-format";
  static final String SWIFTLINT = "swiftlint";
  static final String SWIFT_BUILD = "swift-build";
  static final String SWIFT_TEST = "swift-test";
  static final String XCODEBUILD_TEST = "xcodebuild-test";
  static final String SWIFT_COVERAGE = "swift-coverage";

  /** Copies the source list so the tools stay immutable. */
  SwiftTools {
    sourceDirectories = List.copyOf(sourceDirectories);
  }

  /** Returns whether this project is an Xcode project rather than a SwiftPM package. */
  boolean xcode() {
    return buildTool == BuildTool.XCODE;
  }

  /** Returns the {@code swift format} lint over the source directories, strict and recursive. */
  List<String> swiftFormatCommand() {
    final List<String> command = new ArrayList<>(List.of("swift", "format", "lint"));
    command.addAll(List.of("--strict", "--recursive", "--parallel"));
    command.addAll(sourceDirectories);
    return List.copyOf(command);
  }

  /** Returns the SwiftLint command, which runs the binary the Ward-owned tools package resolved. */
  static List<String> swiftLintCommand() {
    return List.of(SWIFTLINT_BINARY, "lint", "--reporter", "json", "--quiet");
  }

  /** Returns the SwiftPM build of the package and its tests. */
  static List<String> swiftBuildCommand() {
    return List.of("swift", "build", "--build-tests");
  }

  /** Returns the SwiftPM test run, measuring code coverage for the coverage gate. */
  static List<String> swiftTestCommand() {
    return List.of("swift", "test", "--enable-code-coverage");
  }

  /** Returns the command that prints the SwiftPM coverage report's JSON path. */
  static List<String> swiftCoverageCommand() {
    return List.of("swift", "test", "--show-codecov-path");
  }

  /** Returns the Xcode test run on the chosen scheme, with coverage, in the workspace if any. */
  List<String> xcodeTestCommand() {
    final List<String> command = new ArrayList<>(List.of("xcodebuild", "test"));
    workspace.ifPresent(name -> command.addAll(List.of("-workspace", name)));
    command.addAll(List.of("-scheme", scheme, "-enableCodeCoverage", "YES"));
    return List.copyOf(command);
  }

  /** Returns the command that reads the Xcode test result bundle's coverage as JSON. */
  static List<String> xcodeCoverageCommand() {
    return List.of("xcrun", "xccov", "view", "--report", "--json", XCRESULT);
  }
}
