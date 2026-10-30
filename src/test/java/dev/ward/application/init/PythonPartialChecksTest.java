package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The edit hooks narrow ruff and ruff-format to the edited Python files, and skip mypy and tests.
 */
class PythonPartialChecksTest {
  private static final Project UV =
      new Project(Path.of("/shop"), Language.PYTHON, BuildTool.UV, Framework.NONE);

  @Test
  void ruffFormatNarrowsToEditedPythonFiles() {
    final PartialCheckDefaults.Table table =
        PartialCheckDefaults.forGate("ruff-format", UV, QualityPreset.STANDARD).orElseThrow();

    assertThat(table.match()).containsExactly("**/*.py", "**/*.pyi");
    assertThat(table.command())
        .containsExactly("uv", "run", "--group", "ward", "ruff", "format", "--check");
    assertThat(table.args()).containsExactly("{paths}");
  }

  @Test
  void ruffNarrowsToEditedPythonFiles() {
    final PartialCheckDefaults.Table table =
        PartialCheckDefaults.forGate("ruff", UV, QualityPreset.STANDARD).orElseThrow();

    assertThat(table.command())
        .containsExactly(
            "uv", "run", "--group", "ward", "ruff", "check", "--output-format", "json");
    assertThat(table.full()).contains("ruff.toml", ".ruff.toml", "pyproject.toml");
  }

  @Test
  void mypyAndTestsAreNotPartialChecks() {
    assertThat(PartialCheckDefaults.forGate("mypy", UV, QualityPreset.STANDARD)).isEmpty();
    assertThat(PartialCheckDefaults.forGate("pytest", UV, QualityPreset.STANDARD)).isEmpty();
  }
}
