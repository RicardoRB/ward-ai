package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The CI steps set up Python, never a JDK or Node, and install the ward group frozen. */
class PythonCiSetupTest {
  private static final Path ROOT = Path.of("/shop");

  @Test
  void uvSetsUpPythonAndUvThenSyncsFrozen() {
    final String steps = PythonCiSetup.steps(project(BuildTool.UV));

    assertThat(steps)
        .contains(
            "actions/setup-python@v6", "astral-sh/setup-uv@v7", "uv sync --locked --group ward")
        .doesNotContain("setup-java", "setup-node");
  }

  @Test
  void poetryInstallsPoetryAndChecksTheLock() {
    assertThat(PythonCiSetup.steps(project(BuildTool.POETRY)))
        .contains("pipx install poetry", "poetry check --lock && poetry install --with ward");
  }

  @Test
  void pipInstallsTheGroupOrTheRequirementsFile() {
    assertThat(PythonCiSetup.steps(project(BuildTool.PIP)))
        .contains("python -m venv .venv", "requirements-ward.txt", "--group ward");
  }

  private static Project project(final BuildTool manager) {
    return new Project(ROOT, Language.PYTHON, manager, Framework.NONE);
  }
}
