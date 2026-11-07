package dev.ward.infrastructure.analysis.python;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests where the Python stub lookup finds {@code .pyi} text, and what it refuses. */
class PythonStubFilesTest {

  @TempDir Path project;

  @Test
  void projectStubIsFoundBeforeAnyOther() throws IOException {
    write("helpers.pyi", "def make() -> str: ...\n");

    assertThat(new PythonStubFiles(project).text("helpers")).contains("def make() -> str: ...\n");
  }

  @Test
  void virtualenvPackageStubIsFound() throws IOException {
    write(".venv/lib/python3.12/site-packages/extpkg/__init__.pyi", "def run() -> int: ...\n");

    assertThat(new PythonStubFiles(project).text("extpkg")).contains("def run() -> int: ...\n");
  }

  @Test
  void bundledStandardModuleStubIsFound() {
    assertThat(new PythonStubFiles(project).text("json")).isPresent();
  }

  @Test
  void moduleNamesThatEscapeTheProjectAreRefused() throws IOException {
    write("secret.pyi", "def f() -> int: ...\n");

    assertThat(new PythonStubFiles(project).text("../secret")).isEmpty();
    assertThat(new PythonStubFiles(project).text(".secret")).isEmpty();
  }

  @Test
  void unknownModuleHasNoStub() {
    assertThat(new PythonStubFiles(project).text("nothere")).isEmpty();
  }

  private void write(final String relative, final String text) throws IOException {
    final Path file = project.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
