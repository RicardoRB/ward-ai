package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.agent.ClaudeCodeIntegration;
import dev.ward.infrastructure.agent.CopilotIntegration;
import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitPromptHookTest {
  @TempDir Path root;

  @Test
  @DisplayName("an agent without a prompt event")
  void anAgentWithoutAPromptEvent() throws IOException {
    // Given an agent whose hooks have no prompt event that can reject a prompt (Copilot)
    final InitCommandFixture init = new InitCommandFixture(root);
    init.integrations = Set.of(new CopilotIntegration());

    // When I run ward init with that integration and the prompt hook
    final int exit =
        init.run("", "--preset", "standard", "--integration", "copilot", "--prompt-hook", "--yes");

    // Then init says the prompt hook is not supported for it and installs none
    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("Prompt hook is not supported for copilot");
    assertThat(Files.readString(root.resolve(".github/hooks/ward.json")))
        .doesNotContain("userPromptSubmitted", "UserPromptSubmit");
  }

  @Test
  void aSupportedAgentGetsThePromptHookOnlyWhenAskedFor() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);
    init.integrations = Set.of(new ClaudeCodeIntegration());

    final int exit =
        init.run(
            "",
            "--preset",
            "standard",
            "--integration",
            "claude-code",
            "--prompt-hook",
            "--yes"
        );

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).doesNotContain("Prompt hook is not supported");
    assertThat(Files.readString(root.resolve(".claude/settings.json")))
        .contains("UserPromptSubmit");
  }

  @Test
  @DisplayName("the prompt hook also turns on prompt scanning")
  void thePromptHookAlsoTurnsOnPromptScanning() throws IOException {
    // Given a project, a supported agent and the secrets gate
    final InitCommandFixture init = new InitCommandFixture(root);

    // When I run ward init with --prompt-hook
    final int exit =
        init.run(
            "",
            "--preset",
            "standard",
            "--gate",
            "secrets",
            "--integration",
            "claude-code",
            "--prompt-hook",
            "--yes"
        );

    // Then ward.toml enables the prompts table of the secrets check
    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.configuration()).contains("[checks.secrets.prompts]\nenabled = true\n");
  }

  @Test
  @DisplayName("without the prompt hook nothing about prompts is written")
  void withoutThePromptHookNothingAboutPromptsIsWritten() throws IOException {
    // Given the same project
    final InitCommandFixture init = new InitCommandFixture(root);

    // When I run ward init without --prompt-hook
    final int exit =
        init.run(
            "",
            "--preset",
            "standard",
            "--gate",
            "secrets",
            "--integration",
            "claude-code",
            "--yes"
        );

    // Then neither ward.toml nor the Claude settings mention prompts
    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.configuration()).doesNotContain("prompts");
    assertThat(Files.readString(root.resolve(".claude/settings.json")))
        .doesNotContain("UserPromptSubmit");
  }
}
