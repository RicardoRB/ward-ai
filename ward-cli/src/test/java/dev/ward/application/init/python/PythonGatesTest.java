package dev.ward.application.init.python;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The exact commands Python gates run for each package manager and preset. */
class PythonGatesTest {
  private static final String RUN = "run";
  private static final String MYPY = "mypy";
  private static final String COVERAGE = "coverage";

  @Test
  void uvRunsEveryToolThroughTheWardGroup() {
    final List<InitGateOption> gates = gates(BuildTool.UV, QualityPreset.STANDARD, false);

    assertThat(command(gates, MYPY))
        .containsExactly("uv", RUN, "--group", "ward", MYPY, ".", "-O", "json");
    assertThat(command(gates, "pytest"))
        .containsExactly(
            "uv", RUN, "--group", "ward", COVERAGE, "run", "-m", "pytest", "--ignore=mutants");
    assertThat(command(gates, COVERAGE))
        .containsExactly(
            "uv", RUN, "--group", "ward", COVERAGE, "lcov", "-o", ".ward/reports/coverage.lcov");
  }

  @Test
  void poetryRunsThroughItsEnvironment() {
    assertThat(command(gates(BuildTool.POETRY, QualityPreset.STANDARD, false), "mypy"))
        .containsExactly("poetry", RUN, MYPY, ".", "-O", "json");
  }

  @Test
  void pipRunsFromTheVirtualEnvironment() {
    assertThat(command(gates(BuildTool.PIP, QualityPreset.STANDARD, false), "mypy"))
        .containsExactly(".venv/bin/mypy", ".", "-O", "json");
  }

  @Test
  void standardPresetSelectsThreeGatesAndKeepsMutationOptIn() {
    final PythonGates gates = new PythonGates();

    assertThat(gates.presetGates(QualityPreset.STANDARD))
        .containsExactly("mypy", "pytest", "coverage");
    assertThat(gates(BuildTool.UV, QualityPreset.STANDARD, false).stream().map(InitGateOption::id))
        .contains("mutmut")
        .doesNotContain("pip-audit");
  }

  @Test
  void strictAddsMutationTestingWithItsMinimum() {
    final PythonGates gates = new PythonGates();

    assertThat(gates.presetGates(QualityPreset.STRICT))
        .containsExactly("mypy", "pytest", "coverage", "mutmut");
    assertThat(setting(gates(BuildTool.UV, QualityPreset.STRICT, false), "mutmut", "minimum"))
        .isEqualTo("60");
    assertThat(setting(gates(BuildTool.UV, QualityPreset.STANDARD, false), "mutmut", "minimum"))
        .isNull();
  }

  @Test
  void strictAddsItsFlagsOnlyToUserOwnedConfigs() {
    final List<InitGateOption> owned = gates(BuildTool.UV, QualityPreset.STRICT, true);
    final List<InitGateOption> ward = gates(BuildTool.UV, QualityPreset.STRICT, false);

    assertThat(command(owned, MYPY)).contains("--strict");
    assertThat(command(owned, "pytest"))
        .containsSequence(COVERAGE, "run", "--branch", "-m", "pytest", "--ignore=mutants");
    assertThat(command(ward, MYPY)).doesNotContain("--strict");
  }

  private static List<InitGateOption> gates(
      final BuildTool manager, final QualityPreset preset, final boolean owned) {
    return new PythonGates()
        .analysisGates(
            new PythonTools(manager, true, preset, Framework.NONE, owned, owned, Optional.empty()));
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
