package dev.ward.application.init;

import static dev.ward.application.init.SwiftGateFixtures.swiftPm;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Checks the strict Swift gate commands: warnings as errors, SwiftLint's strict layer, coverage.
 */
class SwiftStrictGatesTest {
  @Test
  void strictSwiftPmGatesAddWarningsAsErrorsAndTheStricterMinimum() {
    final List<InitGateOption> gates = gates(swiftPm(QualityPreset.STRICT, false));

    assertThat(gates.stream().map(InitGateOption::id))
        .containsExactly(
            "swift-format", "swiftlint", "swift-build", "swift-test", "swift-coverage");
    assertThat(command(gates, "swiftlint"))
        .endsWith("lint", "--reporter", "json", "--quiet", "--strict")
        .doesNotContain("--config");
    assertThat(command(gates, "swift-build"))
        .containsExactly(
            "swift",
            "build",
            "--build-tests",
            "-Xswiftc",
            "-warnings-as-errors",
            "-Xswiftc",
            "-strict-concurrency=complete");
    assertThat(command(gates, "swift-test"))
        .containsExactly(
            "swift",
            "test",
            "--enable-code-coverage",
            "-Xswiftc",
            "-warnings-as-errors",
            "-Xswiftc",
            "-strict-concurrency=complete");
    assertThat(command(gates, "swift-coverage"))
        .containsExactly(
            "swift",
            "test",
            "--show-codecov-path",
            "-Xswiftc",
            "-warnings-as-errors",
            "-Xswiftc",
            "-strict-concurrency=complete");
    assertThat(setting(gates, "swift-coverage", "minimum")).isEqualTo("85");
  }

  @Test
  void strictOverAUserSwiftLintConfigPassesTheStrictLayerAfterTheUsersOwn() {
    final List<InitGateOption> gates = gates(swiftPm(QualityPreset.STRICT, true));

    assertThat(command(gates, "swiftlint"))
        .endsWith(
            "lint",
            "--reporter",
            "json",
            "--quiet",
            "--strict",
            "--config",
            ".swiftlint.yml",
            "--config",
            ".ward/swiftlint-strict.yml");
  }

  @Test
  void strictXcodeTestOverridesTheWarningsAndConcurrencyBuildSettings() {
    final List<InitGateOption> gates =
        gates(SwiftGateFixtures.xcode(QualityPreset.STRICT, "iOS Simulator", Optional.empty()));

    assertThat(command(gates, "xcodebuild-test"))
        .containsExactly(
            "xcodebuild",
            "test",
            "-scheme",
            "Shop",
            "-enableCodeCoverage",
            "YES",
            "SWIFT_TREAT_WARNINGS_AS_ERRORS=YES",
            "SWIFT_STRICT_CONCURRENCY=complete");
    assertThat(setting(gates, "swift-coverage", "minimum")).isEqualTo("85");
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
