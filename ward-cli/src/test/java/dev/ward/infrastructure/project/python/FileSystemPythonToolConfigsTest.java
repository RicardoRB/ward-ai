package dev.ward.infrastructure.project.python;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finds a Python tool's own config, and ignores the ones Ward wrote. */
class FileSystemPythonToolConfigsTest {
  @TempDir Path root;

  @Test
  void standaloneFileCountsAsUserOwned() throws IOException {
    Files.writeString(root.resolve("mypy.ini"), "[mypy]\nstrict = True\n");

    assertThat(new FileSystemPythonToolConfigs().userConfig(root, "mypy"))
        .contains(root.resolve("mypy.ini"));
  }

  @Test
  void wardMarkedFileDoesNotCount() throws IOException {
    Files.writeString(root.resolve(".coveragerc"), "# managed-by: ward preset=standard\n[run]\n");

    assertThat(new FileSystemPythonToolConfigs().userConfig(root, "coverage")).isEmpty();
  }

  @Test
  void pyprojectTableCountsAsUserOwned() throws IOException {
    Files.writeString(
        root.resolve("pyproject.toml"),
        "[project]\nname = \"shop\"\n\n[tool.mypy]\nstrict = true\n");

    assertThat(new FileSystemPythonToolConfigs().userConfig(root, "mypy"))
        .contains(root.resolve("pyproject.toml"));
  }

  @Test
  void setupCfgSectionCountsAsUserOwned() throws IOException {
    Files.writeString(
        root.resolve("setup.cfg"), "[metadata]\nname = shop\n\n[coverage:run]\nbranch = True\n");

    assertThat(new FileSystemPythonToolConfigs().userConfig(root, "coverage"))
        .contains(root.resolve("setup.cfg"));
  }

  @Test
  void projectWithoutConfigsHasNone() {
    assertThat(new FileSystemPythonToolConfigs().userConfig(root, "mypy")).isEmpty();
  }
}
