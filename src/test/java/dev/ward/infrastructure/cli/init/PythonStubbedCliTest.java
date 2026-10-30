package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Task 6.1: init on a uv project, then {@code check --profile ci} with the stub uv, offline. Every
 * gate passes, and the files init writes are the ones the spec names.
 */
class PythonStubbedCliTest {
  private static final String PIP = "python-pip";
  private static final int GATES_IN_STANDARD = 5;
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
  private static final Pattern STATUS =
      Pattern.compile("\"name\":\"([^\"]+)\"(?:,\"type\":\"[^\"]*\")?,\"status\":\"([A-Z]+)\"");

  @TempDir Path project;

  @ParameterizedTest(name = "{0} with stub tools")
  @ValueSource(strings = {"python-uv", "python-poetry", "python-pip"})
  void standardInitThenCiCheckPassesEveryGateWithStubTools(final String fixture)
      throws IOException, InterruptedException {
    copy(FIXTURES.resolve(fixture), project);
    if (PIP.equals(fixture)) {
      venvWithStubTools(project);
    }

    final PythonStubbedRun.Result init =
        PythonStubbedRun.ward(
            project, "init", "--preset", "standard", "--integration", "none", "--yes");
    assertThat(init.exit()).as(init.stdout() + init.stderr()).isZero();
    assertThat(Files.readString(project.resolve("pyproject.toml")))
        .contains("ruff==0.17.1", "mypy==2.4.0");
    assertThat(Files.isRegularFile(project.resolve("ruff.toml"))).isTrue();
    assertThat(Files.isRegularFile(project.resolve(".coveragerc"))).isTrue();

    final PythonStubbedRun.Result check =
        PythonStubbedRun.ward(project, "check", "--profile", "ci", "--format", "json");
    assertThat(check.exit()).as(check.stdout()).isEqualTo(ExitCodes.OK);
    final Matcher gate = STATUS.matcher(check.stdout());
    int gates = 0;
    while (gate.find()) {
      gates++;
      assertThat(gate.group(2)).as("gate %s", gate.group(1)).isEqualTo("PASSED");
    }
    assertThat(gates).as("gates in the report").isEqualTo(GATES_IN_STANDARD);
  }

  /**
   * pip runs its tools from {@code .venv/bin}: each name links to the stub, which answers by name.
   */
  private static void venvWithStubTools(final Path project) throws IOException {
    final Path bin = Files.createDirectories(project.resolve(".venv/bin"));
    final Path stub = Path.of("src/test/resources/fixtures/python-project-bin/uv").toAbsolutePath();
    for (final String tool : List.of("python", "ruff", "mypy", "coverage", "pytest", "pip-audit")) {
      Files.createSymbolicLink(bin.resolve(tool), stub);
    }
  }

  private static void copy(final Path source, final Path target) throws IOException {
    try (Stream<Path> files = Files.walk(source)) {
      for (final Path file : files.toList()) {
        final Path destination = target.resolve(source.relativize(file).toString());
        if (Files.isDirectory(file)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(file, destination);
        }
      }
    }
  }
}
