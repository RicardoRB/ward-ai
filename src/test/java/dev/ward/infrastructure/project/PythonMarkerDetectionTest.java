package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which build files make a directory a Python project, and how Maven keeps precedence. */
class PythonMarkerDetectionTest {
  private static final String PYPROJECT = "pyproject.toml";

  @TempDir Path root;

  private ProjectTree tree;

  @Test
  void setupPyMarkerIsPythonProject() throws IOException {
    write("setup.py", "from setuptools import setup\n");

    assertThat(FileSystemProjectInspection.projectAt(tree.path("")))
        .hasValueSatisfying(
            project -> {
              assertThat(project.language()).isEqualTo(Language.PYTHON);
              assertThat(project.buildTool()).isEqualTo(BuildTool.PIP);
            });
  }

  @Test
  void requirementsFileWithSuffixIsPythonProject() throws IOException {
    write("requirements-dev.txt", "pytest\n");

    assertThat(FileSystemProjectInspection.projectAt(tree.path("")))
        .hasValueSatisfying(
            project -> {
              assertThat(project.language()).isEqualTo(Language.PYTHON);
              assertThat(project.buildTool()).isEqualTo(BuildTool.PIP);
            });
  }

  @Test
  void mavenMarkerWinsOverPyproject() throws IOException {
    write("pom.xml", "<project/>", PYPROJECT, "[project]\nname = \"app\"\n");

    assertThat(FileSystemProjectInspection.projectAt(tree.path("")))
        .hasValueSatisfying(project -> assertThat(project.buildTool()).isEqualTo(BuildTool.MAVEN));
  }

  private void write(final String... files) throws IOException {
    tree = new ProjectTree(root);
    for (int i = 0; i < files.length; i += 2) {
      tree.write(files[i], files[i + 1]);
    }
  }
}
