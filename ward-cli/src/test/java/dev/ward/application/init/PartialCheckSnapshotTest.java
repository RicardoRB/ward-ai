package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The partial-run table each toolchain's gates return today, pinned before the tables move behind
 * their toolchain support. A change to any of these tables is a change to what init writes.
 */
class PartialCheckSnapshotTest {
  private static final Project UV =
      new Project(Path.of("/shop"), Language.PYTHON, BuildTool.UV, Framework.NONE);

  @Test
  void pythonGatesKeepTheirTables() {
    assertThat(table("ruff", UV, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(
                PYTHON_SOURCES,
                List.of("ruff.toml", ".ruff.toml", "pyproject.toml"),
                PATHS,
                null,
                null,
                List.of(
                    "uv", "run", "--group", "ward", "ruff", "check", "--output-format", "json")));
  }

  @Test
  void neutralGatesKeepTheirTablesForEveryProject() {
    assertThat(table("semgrep", MAVEN, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(WHOLE_PROJECT, List.of(".semgrep*"), PATHS, null, null));
    assertThat(table("gitleaks", PNPM, QualityPreset.STANDARD))
        .isEqualTo(
            new PartialCheckDefaults.Table(
                WHOLE_PROJECT,
                List.of(".gitleaks*"),
                List.of("--no-git", "--source={paths}"),
                null,
                null));
    assertThat(PartialCheckDefaults.forOpenApi(SupportedQualityGates.OPENAPI_LINT, "api.yaml"))
        .contains(
            new PartialCheckDefaults.Table(List.of("api.yaml"), List.of(), List.of(), null, null));
  }

  private static PartialCheckDefaults.Table table(
      final String gate, final Project project, final QualityPreset preset) {
    return PartialCheckDefaults.forGate(gate, project, preset, List.of()).orElseThrow();
  }
}
