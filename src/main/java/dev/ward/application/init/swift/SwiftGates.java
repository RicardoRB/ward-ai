package dev.ward.application.init.swift;

import dev.ward.application.init.SourceLanguageGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Swift's gates on SwiftPM or Xcode: formatting, SwiftLint, the build tool's test gates, coverage
 * and the Harmonize architecture rules. Strict selects the same gates, with stricter commands, a
 * coverage minimum of 85% and the architecture rules.
 */
public final class SwiftGates implements SourceLanguageGates<SwiftTools> {
  private static final String MINIMUM = "minimum";
  private static final String PLATFORM = "platform";

  /**
   * Every gate Swift's standard preset can enable; {@link SwiftInitSupport} keeps the tool's own.
   */
  private static final List<String> STANDARD =
      List.of(
          SwiftTools.SWIFT_FORMAT,
          SwiftTools.SWIFTLINT,
          SwiftTools.SWIFT_BUILD,
          SwiftTools.SWIFT_TEST,
          SwiftTools.XCODEBUILD_TEST,
          SwiftTools.SWIFT_COVERAGE);

  /** Strict adds the Harmonize architecture rules to the standard gates. */
  private static final List<String> STRICT =
      List.of(
          SwiftTools.SWIFT_FORMAT,
          SwiftTools.SWIFTLINT,
          SwiftTools.SWIFT_BUILD,
          SwiftTools.SWIFT_TEST,
          SwiftTools.XCODEBUILD_TEST,
          SwiftTools.SWIFT_COVERAGE,
          SwiftTools.HARMONIZE);

  @Override
  public List<InitGateOption> analysisGates(final SwiftTools swift) {
    final List<InitGateOption> gates = new ArrayList<>();
    gates.add(
        option(
            swift,
            SwiftTools.SWIFT_FORMAT,
            "Checks formatting with swift-format, strict and recursive over the source directories.",
            swift.swiftFormatCommand(),
            Map.of()));
    gates.add(
        option(
            swift,
            SwiftTools.SWIFTLINT,
            "Lints sources with SwiftLint, built from the Ward-owned tools package; strict fails on"
                + " warnings.",
            swift.swiftLintGateCommand(),
            Map.of()));
    gates.addAll(testGates(swift));
    gates.add(
        option(
            swift,
            SwiftTools.SWIFT_COVERAGE,
            "Fails below the line coverage minimum of the test run.",
            coverageCommand(swift),
            Map.of(MINIMUM, swift.coverageMinimum())));
    gates.add(
        option(
            swift,
            SwiftTools.HARMONIZE,
            "Checks architecture rules with Harmonize, generated for the style in"
                + " .ward/swift-architecture; the first run builds Harmonize and swift-syntax.",
            SwiftTools.harmonizeCommand(),
            Map.of()));
    return List.copyOf(gates);
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? STRICT : STANDARD;
  }

  /** The build tool's test gates: {@code swift-build} and {@code swift-test}, or Xcode's test. */
  private static List<InitGateOption> testGates(final SwiftTools swift) {
    if (swift.xcode()) {
      return List.of(
          option(
              swift,
              SwiftTools.XCODEBUILD_TEST,
              "Runs the Xcode scheme's tests with code coverage on the platform below.",
              swift.xcodeTestCommand(),
              Map.of(PLATFORM, swift.platform())));
    }
    return List.of(
        option(
            swift,
            SwiftTools.SWIFT_BUILD,
            "Builds the package and its tests.",
            swift.swiftBuildCommand(),
            Map.of()),
        option(
            swift,
            SwiftTools.SWIFT_TEST,
            "Runs the package's tests with code coverage.",
            swift.swiftTestCommand(),
            Map.of()));
  }

  private static List<String> coverageCommand(final SwiftTools swift) {
    return swift.xcode() ? SwiftTools.xcodeCoverageCommand() : swift.swiftCoverageCommand();
  }

  /** Returns a gate choice whose availability is the build tool's executable on the path. */
  private static InitGateOption option(
      final SwiftTools swift,
      final String id,
      final String description,
      final List<String> command,
      final Map<String, String> settings) {
    final String name = swift.xcode() ? "Xcode" : "SwiftPM";
    return new InitGateOption(
        id,
        description,
        command,
        swift.available(),
        swift.available()
            ? name + " command available."
            : name + " is unavailable; install Xcode 26 or newer before running this gate.",
        settings);
  }

  /** Whether {@code gate} exists for {@code buildTool}; each build tool has its own test gates. */
  static boolean appliesTo(final BuildTool buildTool, final String gate) {
    return buildTool == BuildTool.XCODE
        ? !SwiftTools.SWIFT_BUILD.equals(gate) && !SwiftTools.SWIFT_TEST.equals(gate)
        : !SwiftTools.XCODEBUILD_TEST.equals(gate);
  }
}
