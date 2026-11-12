package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init offers the native {@code secrets} check, selects it in no preset, and knows no Gitleaks. */
class InitSecretsGateTest {

  @TempDir Path root;

  @Test
  void theSecretsGateIsWrittenAsAUsesCheckWithoutACommandInTheFastProfiles() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run("", "--integration", "none", "--gate", "secrets");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    final String toml = init.configuration();
    assertThat(toml)
        .contains(
            "[checks.secrets]",
            "uses = \"secrets\"",
            "profiles = [\"default\", \"pre-commit\", \"pre-push\", \"ci\", \"edit\"]",
            "[checks.secrets.paths]",
            "match = [\"**\"]",
            "full = [\"ward-rules.toml\"]");
    final String table = toml.substring(toml.indexOf("[checks.secrets]"));
    assertThat(table.substring(0, table.indexOf("[checks.secrets.paths]")))
        .doesNotContain("command");
    assertThat(table).doesNotContain("args");
  }

  @Test
  void noPresetSelectsTheSecretsCheck() throws IOException {
    for (final String preset : new String[] {"standard", "strict"}) {
      final InitCommandFixture init = new InitCommandFixture(root);

      assertThat(init.run("", "--yes", "--preset", preset, "--integration", "none"))
          .as(preset)
          .isEqualTo(ExitCodes.OK);
      assertThat(init.configuration()).as(preset).doesNotContain("secrets");
    }
  }

  @Test
  void theGitleaksGateIsUnknownAndExitsWithTwo() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run("", "--integration", "none", "--gate", "gitleaks");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains("gitleaks").contains("secrets");
  }
}
