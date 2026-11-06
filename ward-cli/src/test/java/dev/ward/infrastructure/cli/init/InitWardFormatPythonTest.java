package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Python init: {@code ward-format} checks formatting and ruff keeps linting. */
@DisabledOnOs(OS.WINDOWS)
class InitWardFormatPythonTest {
  @TempDir Path project;

  @Test
  void pythonInitWritesWardFormatAndNoRuffFormatGate() throws IOException, InterruptedException {
    copyFixture("python-uv", project);

    final PythonStubbedRun.Result init =
        PythonStubbedRun.ward(
            project, "init", "--preset", "standard", "--integration", "none", "--yes");

    assertThat(init.exit()).as(init.stdout() + init.stderr()).isZero();
    final String toml = Files.readString(project.resolve("ward.toml"));
    assertThat(toml)
        .contains("[checks.ward-format]", "[checks.ruff]")
        .doesNotContain("[checks.ruff-format]");
    assertThat(Files.readString(project.resolve(".editorconfig")))
        .contains("[*.py]\nindent_size = 4\n");
  }

  private static void copyFixture(final String name, final Path destination) throws IOException {
    final Path source = Path.of("src/test/resources/fixtures").resolve(name);
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.toList()) {
        final Path target = destination.resolve(source.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(file, target);
        }
      }
    }
  }
}
