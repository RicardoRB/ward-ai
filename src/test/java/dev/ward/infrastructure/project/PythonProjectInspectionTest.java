package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The package manager of a Python project, from its lock files and pyproject.toml tables. */
class PythonProjectInspectionTest {
  private static final String PYPROJECT = "pyproject.toml";
  private static final String PROJECT_ONLY = "[project]\nname = \"app\"\n";

  @TempDir Path root;

  private ProjectTree tree;

  @Test
  void uvLockFileSelectsUv() throws IOException {
    write(PYPROJECT, PROJECT_ONLY, "uv.lock", "");

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.UV);
  }

  @Test
  void uvTableWithoutLockFileSelectsUv() throws IOException {
    write(PYPROJECT, PROJECT_ONLY + "[tool.uv]\ndev-dependencies = []\n");

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.UV);
  }

  @Test
  void uvWorkspaceTableSelectsUv() throws IOException {
    write(PYPROJECT, "[tool.uv.workspace]\nmembers = [\"packages/*\"]\n");

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.UV);
  }

  @Test
  void uvWinsOverPoetry() throws IOException {
    write(PYPROJECT, PROJECT_ONLY + "[tool.poetry]\nname = \"app\"\n", "uv.lock", "");

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.UV);
  }

  @Test
  void poetryLockFileSelectsPoetry() throws IOException {
    write(PYPROJECT, PROJECT_ONLY, "poetry.lock", "");

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.POETRY);
  }

  @Test
  void poetryTableSelectsPoetry() throws IOException {
    write(PYPROJECT, PROJECT_ONLY + "[tool.poetry]\nname = \"app\"\n");

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.POETRY);
  }

  @Test
  void pdmLockFileSelectsPdm() throws IOException {
    write(PYPROJECT, PROJECT_ONLY, "pdm.lock", "");

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.PDM);
  }

  @Test
  void hatchTableSelectsHatch() throws IOException {
    write(PYPROJECT, PROJECT_ONLY + "[tool.hatch.version]\npath = \"app/__init__.py\"\n");

    assertThat(inspect().buildTool()).isEqualTo(BuildTool.HATCH);
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
