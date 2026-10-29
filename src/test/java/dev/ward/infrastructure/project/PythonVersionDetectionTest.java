package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The target Python version of a Python project, from .python-version or requires-python. */
class PythonVersionDetectionTest {
  private static final String PYPROJECT = "pyproject.toml";

  @TempDir Path root;

  private ProjectTree tree;

  @Test
  void pythonVersionFileWithPatchReleaseGivesMajorMinor() throws IOException {
    write(PYPROJECT, "[project]\nname = \"app\"\n", ".python-version", "3.12.4\n");

    assertThat(inspect().languageVersion()).isEqualTo("3.12");
  }

  @Test
  void pythonVersionFileWithInterpreterPrefixGivesMajorMinor() throws IOException {
    write(".python-version", "cpython-3.13\n");

    assertThat(inspect().languageVersion()).isEqualTo("3.13");
  }

  @Test
  void versionFileWinsOverRequiresPython() throws IOException {
    write(PYPROJECT, "[project]\nrequires-python = \">=3.11\"\n", ".python-version", "3.13\n");

    assertThat(inspect().languageVersion()).isEqualTo("3.13");
  }

  @Test
  void requiresPythonLowerBoundGivesVersion() throws IOException {
    write(PYPROJECT, "[project]\nrequires-python = \">=3.11,<4\"\n");

    assertThat(inspect().languageVersion()).isEqualTo("3.11");
  }

  @Test
  void missingVersionDefaultsToFourteen() throws IOException {
    write(PYPROJECT, "[project]\nname = \"app\"\n");

    assertThat(inspect().languageVersion()).isEqualTo("3.14");
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
