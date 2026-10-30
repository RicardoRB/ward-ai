package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Projects with no manager file or table are pip projects; a bad pyproject.toml never fails them.
 */
class PythonManagerFallbackTest {
  private static final String PYPROJECT = "pyproject.toml";
  private static final String PROJECT_ONLY = "[project]\nname = \"app\"\n";
  private static final String MALFORMED = "[project\nname = ";

  @TempDir Path root;

  private ProjectTree tree;

  @Test
  void projectTableWithoutManagerSelectsPip() throws IOException {
    write(PYPROJECT, PROJECT_ONLY);

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.PIP);
  }

  @Test
  void setupPyWithoutPyprojectSelectsPip() throws IOException {
    write("setup.py", "from setuptools import setup\n");

    final Project project = inspect();

    assertThat(project.language()).isEqualTo(Language.PYTHON);
    assertThat(project.buildTool()).isEqualTo(BuildTool.PIP);
  }

  @Test
  void malformedPyprojectFallsBackToPip() throws IOException {
    write(PYPROJECT, MALFORMED);

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.PIP);
  }

  @Test
  void lockFileStillDecidesForMalformedPyproject() throws IOException {
    write(PYPROJECT, MALFORMED, "poetry.lock", "");

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.POETRY);
  }

  @Test
  void pipProjectWithVirtualEnvironmentIsWrapped() throws IOException {
    write(PYPROJECT, PROJECT_ONLY);
    Files.createDirectories(tree.path(".venv"));

    assertThat(inspect().wrapperAvailable()).isTrue();
  }

  @Test
  void pipProjectWithoutVirtualEnvironmentIsNotWrapped() throws IOException {
    write(PYPROJECT, PROJECT_ONLY);

    assertThat(inspect().wrapperAvailable()).isFalse();
  }

  private Project inspect() {
    return PythonProjectInspection.project(tree.path(""));
  }

  private void write(final String... files) throws IOException {
    tree = new ProjectTree(root);
    for (int i = 0; i < files.length; i += 2) {
      tree.write(files[i], files[i + 1]);
    }
  }
}
