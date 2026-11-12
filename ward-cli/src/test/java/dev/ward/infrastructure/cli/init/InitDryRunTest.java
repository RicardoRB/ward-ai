package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectSnapshot;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.python.PythonBuildToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** --dry-run previews without writing, and compatibility approval shows in the plan. */
class InitDryRunTest {

  @TempDir Path root;

  @Test
  void dryRunPrintsThePlanAndChangesNothing() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit =
        init.run(
            "",
            "--dry-run",
            "--preset",
            "strict",
            "--integration",
            "none",
            "--architecture",
            "layered");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("Ward will make these changes:", "create     ward.toml", "modify     pom.xml");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  @Disabled(
      "Init offers no gate whose tool it can install since the security rules replaced semgrep; the decision on these tests is open")
  void gateToolConsentIsPlannedWithoutInstalling() throws IOException {
    Files.createDirectories(root.resolve(".venv"));
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/app.py"), "x = 1\n");
    Files.writeString(
        root.resolve("pyproject.toml"), "[project]\nname = \"demo\"\nversion = \"1\"\n");
    final InitCommandFixture init =
        new InitCommandFixture(
            root, new PythonBuildToolConfigurator(new FakeCommandExecutor(0, "", "")), "brew");
    Files.delete(root.resolve("pom.xml"));

    final int exit =
        init.run(
            "",
            "--dry-run",
            "--integration",
            "none",
            "--gate",
            "semgrep",
            "--install-tool",
            "semgrep");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("brew install semgrep");
    assertThat(root.resolve(".ward")).doesNotExist();
  }
}
