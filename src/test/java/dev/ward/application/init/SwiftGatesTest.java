package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Checks the exact Swift gate commands, their platform and minimum, and their order. */
class SwiftGatesTest {
  private static final List<String> SOURCES = List.of("Package.swift", "Sources", "Tests");

  @Test
  void swiftPackageGatesRunSwiftPmInOrder() {
    final List<InitGateOption> gates = gates(swiftPm());

    assertThat(gates.stream().map(InitGateOption::id))
        .containsExactly(
            "swift-format", "swiftlint", "swift-build", "swift-test", "swift-coverage");
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
    assertThat(command(gates, "swift-coverage"))
        .containsExactly("swift", "test", "--show-codecov-path");
    assertThat(setting(gates, "swift-coverage", "minimum")).isEqualTo("80");
  }

  @Test
  void xcodeGatesRunTheSchemeWithPlatformAndCoverageFromTheResultBundle() {
    final List<InitGateOption> gates = gates(xcode("iOS Simulator", Optional.empty()));

    assertThat(gates.stream().map(InitGateOption::id))
        .containsExactly("swift-format", "swiftlint", "xcodebuild-test", "swift-coverage");
    assertThat(command(gates, "xcodebuild-test"))
        .containsExactly("xcodebuild", "test", "-scheme", "Shop", "-enableCodeCoverage", "YES");
    assertThat(setting(gates, "xcodebuild-test", "platform")).isEqualTo("iOS Simulator");
    assertThat(command(gates, "swift-coverage"))
        .containsExactly(
            "xcrun", "xccov", "view", "--report", "--json", ".ward/reports/xcode/tests.xcresult");
    assertThat(setting(gates, "swift-coverage", "minimum")).isEqualTo("80");
  }

  @Test
  void xcodeTestUsesTheWorkspaceWhenThereIsOne() {
    final List<InitGateOption> gates = gates(xcode("macOS", Optional.of("Shop.xcworkspace")));

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
  void standardPresetNamesEveryGateThatTheToolHasAndStrictIsRefused() {
    assertThat(new SwiftGates().presetGates(QualityPreset.STANDARD)).contains("swift-coverage");
    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () -> new SwiftGates().presetGates(QualityPreset.STRICT)))
        .hasMessageContaining("--preset standard");
  }

  private static SwiftTools swiftPm() {
    return new SwiftTools(
        BuildTool.SWIFTPM, true, QualityPreset.STANDARD, SOURCES, Optional.empty(), null, null);
  }

  private static SwiftTools xcode(final String platform, final Optional<String> workspace) {
    return new SwiftTools(
        BuildTool.XCODE, true, QualityPreset.STANDARD, SOURCES, workspace, "Shop", platform);
  }

  private static List<InitGateOption> gates(final SwiftTools tools) {
    return new SwiftGates().analysisGates(tools);
  }

  private static List<String> command(final List<InitGateOption> gates, final String id) {
    return gates.stream().filter(gate -> gate.id().equals(id)).findFirst().orElseThrow().command();
  }

  private static String setting(
      final List<InitGateOption> gates, final String id, final String key) {
    final Map<String, String> settings =
        gates.stream().filter(gate -> gate.id().equals(id)).findFirst().orElseThrow().settings();
    return settings.get(key);
  }
}
