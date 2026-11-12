package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectSnapshot;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
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
  void gateToolConsentIsPlannedWithoutInstalling() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root, new PomToolConfigurator(), "brew");

    final int exit =
        init.run(
            "",
            "--dry-run",
            "--integration",
            "none",
            "--gate",
            "tests,gitleaks",
            "--install-tool",
            "gitleaks");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("brew install gitleaks");
    assertThat(root.resolve(".ward")).doesNotExist();
  }
}
