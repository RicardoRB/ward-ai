package dev.ward.application.init.swift;

import dev.ward.domain.init.ArchitectureGates;
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
 * @param ownSwiftLintConfig whether the project keeps its own {@code .swiftlint.yml}, which strict
 *     layers over with {@link #STRICT_CONFIG} instead of replacing
 */
record SwiftTools(
    BuildTool buildTool,
    boolean available,
    QualityPreset preset,
    List<String> sourceDirectories,
    Optional<String> workspace,
    String scheme,
    String platform,
    boolean ownSwiftLintConfig) {
  /**
   * The SwiftLint executable that {@code swift package resolve} downloads for the Ward tools
   * package. SwiftPM cannot run a dependency's executable product with {@code swift run}, so the
   * gate runs the artifact directly. The path is SwiftPM's artifact layout for the pinned
   * SwiftLintPlugins.
   */
  static final String SWIFTLINT_BINARY =
      ".ward/swift-tools/.build/artifacts/swiftlintplugins/SwiftLintBinary/"
          + "SwiftLintBinary.artifactbundle/macos/swiftlint";

  static final String SWIFTLINT_CONFIG = ".swiftlint.yml";
  static final String STRICT_CONFIG = ".ward/swiftlint-strict.yml";
  static final String STANDARD_COVERAGE = "80";
  static final String STRICT_COVERAGE = "85";
  static final String XCRESULT = ".ward/reports/xcode/tests.xcresult";
  static final String SWIFT_FORMAT = "swift-format";
  static final String SWIFTLINT = "swiftlint";
  static final String SWIFT_BUILD = "swift-build";
  static final String SWIFT_TEST = "swift-test";
  static final String XCODEBUILD_TEST = "xcodebuild-test";
  static final String SWIFT_COVERAGE = "swift-coverage";
  static final String HARMONIZE = "harmonize";

  /** The compiler flags strict adds on SwiftPM, so warnings fail and concurrency is complete. */
  private static final List<String> SWIFTPM_STRICT_FLAGS =
      List.of("-Xswiftc", "-warnings-as-errors", "-Xswiftc", "-strict-concurrency=complete");

  /** The Xcode build settings strict adds to {@code xcodebuild test}, as one argument each. */
  private static final List<String> XCODE_STRICT_SETTINGS =
      List.of("SWIFT_TREAT_WARNINGS_AS_ERRORS=YES", "SWIFT_STRICT_CONCURRENCY=complete");

  /** Copies the source list so the tools stay immutable. */
  SwiftTools {
    sourceDirectories = List.copyOf(sourceDirectories);
  }

  /** Returns whether this project is an Xcode project rather than a SwiftPM package. */
  boolean xcode() {
    return buildTool == BuildTool.XCODE;
  }

  /** Returns whether the preset is strict. */
  boolean strict() {
    return preset == QualityPreset.STRICT;
  }

  /** Returns the line coverage minimum of the preset, in percent. */
  String coverageMinimum() {
    return strict() ? STRICT_COVERAGE : STANDARD_COVERAGE;
  }

  /** Returns the {@code swift format} lint over the source directories, strict and recursive. */
  List<String> swiftFormatCommand() {
    final List<String> command = new ArrayList<>(List.of("swift", "format", "lint"));
    command.addAll(List.of("--strict", "--recursive", "--parallel"));
    command.addAll(sourceDirectories);
    return List.copyOf(command);
  }

  /** Returns the standard SwiftLint command, which runs the binary the tools package resolved. */
  static List<String> swiftLintCommand() {
    return List.of(SWIFTLINT_BINARY, "lint", "--reporter", "json", "--quiet");
  }

  /**
   * Returns the SwiftLint gate command. Strict adds {@code --strict}, which fails on warnings, and
   * over a user config also the strict file, after the user's, so the strict rules layer on top.
   */
  List<String> swiftLintGateCommand() {
    final List<String> command = new ArrayList<>(swiftLintCommand());
    if (strict()) {
      command.add("--strict");
      if (ownSwiftLintConfig) {
        command.addAll(List.of("--config", SWIFTLINT_CONFIG, "--config", STRICT_CONFIG));
      }
    }
    return List.copyOf(command);
  }

  /** Returns the SwiftPM build of the package and its tests, warnings as errors when strict. */
  List<String> swiftBuildCommand() {
    return withStrictFlags(List.of("swift", "build", "--build-tests"));
  }

  /** Returns the SwiftPM test run, measuring coverage, warnings as errors when strict. */
  List<String> swiftTestCommand() {
    return withStrictFlags(List.of("swift", "test", "--enable-code-coverage"));
  }

  /** Appends the SwiftPM strict flags to {@code command} when the preset is strict. */
  private List<String> withStrictFlags(final List<String> command) {
    if (!strict()) {
      return command;
    }
    final List<String> strictCommand = new ArrayList<>(command);
    strictCommand.addAll(SWIFTPM_STRICT_FLAGS);
    return List.copyOf(strictCommand);
  }

  /**
   * Returns the command that prints the SwiftPM coverage report's JSON path, built like the test
   * run.
   */
  List<String> swiftCoverageCommand() {
    return withStrictFlags(List.of("swift", "test", "--show-codecov-path"));
  }

  /** Returns the Xcode test run on the chosen scheme, with coverage, in the workspace if any. */
  List<String> xcodeTestCommand() {
    final List<String> command = new ArrayList<>(List.of("xcodebuild", "test"));
    workspace.ifPresent(name -> command.addAll(List.of("-workspace", name)));
    command.addAll(List.of("-scheme", scheme, "-enableCodeCoverage", "YES"));
    if (strict()) {
      command.addAll(XCODE_STRICT_SETTINGS);
    }
    return List.copyOf(command);
  }

  /**
   * Returns the test run of the architecture rules package, which builds Harmonize on first use.
   */
  static List<String> harmonizeCommand() {
    return List.of("swift", "test", "--package-path", ArchitectureGates.HARMONIZE_PACKAGE);
  }

  /** Returns the command that reads the Xcode test result bundle's coverage as JSON. */
  static List<String> xcodeCoverageCommand() {
    return List.of("xcrun", "xccov", "view", "--report", "--json", XCRESULT);
  }
}
