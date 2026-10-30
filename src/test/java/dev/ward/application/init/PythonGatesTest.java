package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The exact commands Python gates run for each package manager. */
class PythonGatesTest {
  private static final String RUN = "run";

  @Test
  void uvRunsEveryToolThroughTheWardGroup() {
    final List<InitGateOption> gates = gates(BuildTool.UV, false);

    assertThat(command(gates, "ruff-format"))
        .containsExactly("uv", RUN, "--group", "ward", "ruff", "format", "--check", ".");
    assertThat(command(gates, "ruff"))
        .containsExactly(
            "uv", RUN, "--group", "ward", "ruff", "check", ".", "--output-format", "json");
    assertThat(command(gates, "mypy"))
        .containsExactly("uv", RUN, "--group", "ward", "mypy", ".", "-O", "json");
    assertThat(command(gates, "pytest"))
        .containsExactly("uv", RUN, "--group", "ward", "coverage", RUN, "-m", "pytest");
    assertThat(command(gates, "coverage"))
        .containsExactly("uv", RUN, "--group", "ward", "coverage", "report");
  }

  @Test
  void poetryRunsThroughItsEnvironment() {
    assertThat(command(gates(BuildTool.POETRY, false), "mypy"))
        .containsExactly("poetry", RUN, "mypy", ".", "-O", "json");
  }

  @Test
  void pipRunsFromTheVirtualEnvironment() {
    assertThat(command(gates(BuildTool.PIP, false), "pip-audit"))
        .containsExactly(".venv/bin/pip-audit");
  }

  @Test
  void coverageThresholdGoesOnTheCommandOnlyForAUserConfig() {
    assertThat(command(gates(BuildTool.UV, true), "coverage"))
        .containsExactly("uv", RUN, "--group", "ward", "coverage", "report", "--fail-under=80");
  }

  @Test
  void standardPresetSelectsFiveGatesAndKeepsAuditOptIn() {
    final PythonGates gates = new PythonGates();

    assertThat(gates.presetGates(QualityPreset.STANDARD))
        .containsExactly("ruff-format", "ruff", "mypy", "pytest", "coverage");
    assertThat(gates(BuildTool.UV, false).stream().map(InitGateOption::id))
        .contains("pip-audit")
        .doesNotContain("import-linter", "mutmut");
  }

  private static List<InitGateOption> gates(final BuildTool manager, final boolean ownCoverage) {
    return new PythonGates()
        .analysisGates(new PythonTools(manager, true, QualityPreset.STANDARD, ownCoverage));
  }

  private static List<String> command(final List<InitGateOption> gates, final String id) {
    return gates.stream().filter(gate -> gate.id().equals(id)).findFirst().orElseThrow().command();
  }
}
