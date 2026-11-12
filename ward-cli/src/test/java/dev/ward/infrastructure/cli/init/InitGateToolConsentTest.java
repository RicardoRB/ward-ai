package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.python.PythonBuildToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins the wizard question that asks to install a missing gate tool, asked per such gate. */
class InitGateToolConsentTest {
  @TempDir Path root;

  private InitCommandFixture pythonProject(final String... executables) throws IOException {
    Files.createDirectories(root.resolve(".venv"));
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/app.py"), "x = 1\n");
    Files.writeString(
        root.resolve("pyproject.toml"), "[project]\nname = \"demo\"\nversion = \"1\"\n");
    final InitCommandFixture init =
        new InitCommandFixture(
            root, new PythonBuildToolConfigurator(new FakeCommandExecutor(0, "", "")), executables);
    Files.delete(root.resolve("pom.xml"));
    return init;
  }

  @Test
  void aMissingGateToolIsOfferedAndTheAnswerIsPlanned() throws IOException {
    final InitCommandFixture init = pythonProject("brew");

    final int exit =
        init.run(
            true,
            "y\nn\nn\n",
            "--dry-run",
            "--integration",
            "none",
            "--gate",
            "semgrep",
            "--preset",
            "standard");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("requires unavailable tool 'semgrep'", "Install semgrep? [y/N]")
        .contains("brew install semgrep");
  }

  @Test
  void aGateWithoutAMissingToolAsksNoInstallQuestion() throws IOException {
    final InitCommandFixture init = pythonProject("brew", "semgrep");

    final int exit =
        init.run(
            true,
            "n\nn\n",
            "--dry-run",
            "--integration",
            "none",
            "--gate",
            "semgrep",
            "--preset",
            "standard");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).doesNotContain("Install semgrep?");
  }
}
