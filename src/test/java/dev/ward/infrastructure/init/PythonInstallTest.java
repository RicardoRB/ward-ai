package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The install commands per manager: pip also installs a project that has a build system. */
class PythonInstallTest {
  private static final Path ROOT = Path.of("/shop");
  private static final String GROUP = "ward";

  @Test
  void pipInstallsTheGroupAndTheProjectWhenItHasABuildSystem() {
    assertThat(PythonInstall.commands(pip(), true, true, GROUP))
        .containsExactly(
            List.of(".venv/bin/python", "-m", "pip", "install", "--group", GROUP),
            List.of(".venv/bin/python", "-m", "pip", "install", "-e", "."));
  }

  @Test
  void pipWithoutBuildSystemOnlyInstallsTheGroup() {
    assertThat(PythonInstall.commands(pip(), true, false, GROUP))
        .containsExactly(List.of(".venv/bin/python", "-m", "pip", "install", "--group", GROUP));
  }

  @Test
  void pipWithoutPyprojectInstallsTheRequirementsFile() {
    assertThat(PythonInstall.commands(pip(), false, false, GROUP))
        .containsExactly(
            List.of(".venv/bin/python", "-m", "pip", "install", "-r", "requirements-ward.txt"));
  }

  @Test
  void uvSyncsAndPoetryLocksThenInstalls() {
    assertThat(PythonInstall.commands(project(BuildTool.UV), true, true, GROUP))
        .containsExactly(List.of("uv", "sync", "--group", GROUP));
    assertThat(PythonInstall.commands(project(BuildTool.POETRY), true, true, GROUP))
        .containsExactly(List.of("poetry", "lock"), List.of("poetry", "install", "--with", GROUP));
  }

  private static Project pip() {
    return project(BuildTool.PIP);
  }

  private static Project project(final BuildTool manager) {
    return new Project(ROOT, Language.PYTHON, manager, Framework.NONE);
  }
}
