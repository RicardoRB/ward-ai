package dev.ward.application.init.swift;

import static dev.ward.application.init.swift.SwiftGateFixtures.swiftPm;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Checks the exact Swift gate commands, their platform and minimum, and their order. */
class SwiftGatesTest {
  private static final String COVERAGE = "swift-coverage";

  @Test
  void swiftPackageGatesRunSwiftPmInOrder() {
    final List<InitGateOption> gates = gates(swiftPm(QualityPreset.STANDARD, false));

    assertThat(gates.stream().map(InitGateOption::id))
        .containsExactly(
            "swift-format", "swiftlint", "swift-build", "swift-test", COVERAGE, "harmonize");
    assertThat(command(gates, "swift-format"))
        .containsExactly(
            "swift",
            "format",
            "lint",
            "--strict",
            "--recursive",
            "--parallel",
            "Package.swift",
            "Sources",
            "Tests");
    assertThat(command(gates, "swiftlint"))
        .containsExactly(
            ".ward/swift-tools/.build/artifacts/swiftlintplugins/SwiftLintBinary/SwiftLintBinary.artifactbundle/macos/swiftlint",
            "lint",
            "--reporter",
            "json",
            "--quiet");
    assertThat(command(gates, "swift-build")).containsExactly("swift", "build", "--build-tests");
    assertThat(command(gates, "swift-test"))
        .containsExactly("swift", "test", "--enable-code-coverage");
    assertThat(command(gates, COVERAGE)).containsExactly("swift", "test", "--show-codecov-path");
    assertThat(setting(gates, COVERAGE, "minimum")).isEqualTo("80");
  }

  @Test
  void xcodeGatesRunTheSchemeWithPlatformAndCoverageFromTheResultBundle() {
    final List<InitGateOption> gates =
        gates(SwiftGateFixtures.xcode("iOS Simulator", Optional.empty()));

    assertThat(gates.stream().map(InitGateOption::id))
        .containsExactly("swift-format", "swiftlint", "xcodebuild-test", COVERAGE, "harmonize");
    assertThat(command(gates, "xcodebuild-test"))
        .containsExactly("xcodebuild", "test", "-scheme", "Shop", "-enableCodeCoverage", "YES");
    assertThat(setting(gates, "xcodebuild-test", "platform")).isEqualTo("iOS Simulator");
    assertThat(command(gates, COVERAGE))
        .containsExactly(
            "xcrun", "xccov", "view", "--report", "--json", ".ward/reports/xcode/tests.xcresult");
    assertThat(setting(gates, COVERAGE, "minimum")).isEqualTo("80");
  }

  @Test
  void xcodeTestUsesTheWorkspaceWhenThereIsOne() {
    final List<InitGateOption> gates =
        gates(SwiftGateFixtures.xcode("macOS", Optional.of("Shop.xcworkspace")));

    assertThat(command(gates, "xcodebuild-test"))
        .containsExactly(
            "xcodebuild",
            "test",
            "-workspace",
            "Shop.xcworkspace",
            "-scheme",
            "Shop",
            "-enableCodeCoverage",
            "YES");
    assertThat(setting(gates, "xcodebuild-test", "platform")).isEqualTo("macOS");
  }

  @Test
  void standardPresetNamesEveryGateThatTheToolHasAndStrictAddsHarmonize() {
    assertThat(new SwiftGates().presetGates(QualityPreset.STANDARD))
        .contains(COVERAGE)
        .doesNotContain("harmonize");
    assertThat(new SwiftGates().presetGates(QualityPreset.STRICT))
        .containsExactlyElementsOf(
            List.of(
                "swift-format",
                "swiftlint",
                "swift-build",
                "swift-test",
                "xcodebuild-test",
                COVERAGE,
                "harmonize"));
  }

  /** Returns the gates the tools select, in order. */
  private static List<InitGateOption> gates(final SwiftTools tools) {
    return new SwiftGates().analysisGates(tools);
  }

  /** Returns the command of the gate {@code id}. */
  private static List<String> command(final List<InitGateOption> gates, final String id) {
    return gates.stream().filter(gate -> gate.id().equals(id)).findFirst().orElseThrow().command();
  }

  /** Returns the setting {@code key} of the gate {@code id}. */
  private static String setting(
      final List<InitGateOption> gates, final String id, final String key) {
    final Map<String, String> settings =
        gates.stream().filter(gate -> gate.id().equals(id)).findFirst().orElseThrow().settings();
    return settings.get(key);
  }
}
