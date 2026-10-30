package dev.ward.application.init;

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

    assertThat(command(gates, "ruff-format"))
        .containsExactly("uv", RUN, "--group", "ward", "ruff", "format", "--check", ".");
    assertThat(command(gates, "ruff"))
        .containsExactly(
            "uv", RUN, "--group", "ward", "ruff", "check", ".", "--output-format", "json");
    assertThat(command(gates, MYPY))
        .containsExactly("uv", RUN, "--group", "ward", MYPY, ".", "-O", "json");
    assertThat(command(gates, "pytest"))
        .containsExactly(
            "uv", RUN, "--group", "ward", COVERAGE, "run", "-m", "pytest", "--ignore=mutants");
    assertThat(command(gates, COVERAGE))
        .containsExactly("uv", RUN, "--group", "ward", COVERAGE, "report");
  }

  @Test
  void poetryRunsThroughItsEnvironment() {
    assertThat(command(gates(BuildTool.POETRY, QualityPreset.STANDARD, false), "mypy"))
        .containsExactly("poetry", RUN, MYPY, ".", "-O", "json");
  }

  @Test
  void pipRunsFromTheVirtualEnvironment() {
    assertThat(command(gates(BuildTool.PIP, QualityPreset.STANDARD, false), "pip-audit"))
        .containsExactly(".venv/bin/pip-audit");
  }

  @Test
  void coverageThresholdGoesOnTheCommandOnlyForAUserConfig() {
    assertThat(command(gates(BuildTool.UV, QualityPreset.STANDARD, true), "coverage"))
        .containsExactly("uv", RUN, "--group", "ward", COVERAGE, "report", "--fail-under=80");
  }

  @Test
  void standardPresetSelectsFiveGatesAndKeepsAuditAndMutationOptIn() {
    final PythonGates gates = new PythonGates();

    assertThat(gates.presetGates(QualityPreset.STANDARD))
        .containsExactly("ruff-format", "ruff", "mypy", "pytest", "coverage");
    assertThat(gates(BuildTool.UV, QualityPreset.STANDARD, false).stream().map(InitGateOption::id))
        .contains("pip-audit", "mutmut", "import-linter");
  }

  @Test
  void strictAddsMutationTestingWithItsMinimum() {
    final PythonGates gates = new PythonGates();

    assertThat(gates.presetGates(QualityPreset.STRICT))
        .containsExactly(
            "ruff-format", "ruff", "mypy", "pytest", "coverage", "import-linter", "mutmut");
    assertThat(setting(gates(BuildTool.UV, QualityPreset.STRICT, false), "mutmut", "minimum"))
        .isEqualTo("60");
    assertThat(setting(gates(BuildTool.UV, QualityPreset.STANDARD, false), "mutmut", "minimum"))
        .isNull();
  }

  @Test
  void strictAddsItsFlagsOnlyToUserOwnedConfigs() {
    final List<InitGateOption> owned = gates(BuildTool.UV, QualityPreset.STRICT, true);
    final List<InitGateOption> ward = gates(BuildTool.UV, QualityPreset.STRICT, false);

    assertThat(command(owned, "ruff"))
        .containsSequence(
            "--extend-select", "W,C4,SIM,RUF,PTH,C90", "--config", "lint.mccabe.max-complexity=10");
    assertThat(command(owned, MYPY)).contains("--strict");
    assertThat(command(owned, "pytest"))
        .containsSequence(COVERAGE, "run", "--branch", "-m", "pytest", "--ignore=mutants");
    assertThat(command(owned, COVERAGE)).endsWith("--fail-under=85");
    assertThat(command(ward, "ruff")).doesNotContain("--extend-select");
    assertThat(command(ward, MYPY)).doesNotContain("--strict");
    assertThat(command(ward, COVERAGE)).doesNotContain("--fail-under=85");
  }

  private static List<InitGateOption> gates(
      final BuildTool manager, final QualityPreset preset, final boolean owned) {
    return new PythonGates()
        .analysisGates(
            new PythonTools(
                manager, true, preset, Framework.NONE, owned, owned, owned, Optional.empty()));
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
