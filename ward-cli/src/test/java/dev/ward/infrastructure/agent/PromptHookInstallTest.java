package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The prompt hook is opt-in, ownership-marked, and removed with the rest of the integration. */
class PromptHookInstallTest {
  @TempDir Path root;

  static Stream<Arguments> supported() {
    return Stream.of(
        Arguments.of(new ClaudeCodeIntegration(), ".claude/settings.json", "UserPromptSubmit"),
        Arguments.of(new CodexIntegration(), ".codex/hooks.json", "UserPromptSubmit"),
        Arguments.of(new FactoryDroidIntegration(), ".factory/hooks.json", "UserPromptSubmit"),
        Arguments.of(new GeminiIntegration(), ".gemini/settings.json", "BeforeAgent"),
        Arguments.of(new CursorIntegration(), ".cursor/hooks.json", "beforeSubmitPrompt")
    );
  }

  @ParameterizedTest
  @MethodSource("supported")
  void installsThePromptEventOnlyWhenAskedAndRemovesItOnRefresh(
      final AgentIntegration integration,
      final String file,
      final String event
  ) throws IOException {
    integration.integrate(root, IntegrationRequest.INSTALL);
    assertThat(Files.readString(root.resolve(file))).doesNotContain(event);

    final IntegrationResult asked =
        integration.integrate(root, IntegrationRequest.INSTALL.withPromptHook(true));
    assertThat(asked.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(Files.readString(root.resolve(file))).contains(event);
    assertThat(integration.supportsPromptHook()).isTrue();
    assertThat(integration.plan(root, IntegrationRequest.INSTALL.withPromptHook(true)).toString())
        .isNotEmpty();

    integration.integrate(root, IntegrationRequest.INSTALL);
    assertThat(Files.readString(root.resolve(file))).doesNotContain(event);

    integration.integrate(root, IntegrationRequest.INSTALL.withPromptHook(true));
    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    // Claude Code settings are merged, so the file may stay for the user's own keys.
    if (Files.exists(root.resolve(file))) {
      assertThat(Files.readString(root.resolve(file))).doesNotContain(event);
    }
  }

  @ParameterizedTest
  @MethodSource("supported")
  void aUserOwnedConfigurationStaysAConflictWithThePromptHook(
      final AgentIntegration integration,
      final String file,
      final String event
  ) throws IOException {
    final Path config = root.resolve(file);
    Files.createDirectories(config.getParent());
    Files.writeString(config, "{\"user\": true}\n");

    final IntegrationResult result =
        integration.integrate(root, IntegrationRequest.INSTALL.withPromptHook(true));

    if (integration instanceof ClaudeCodeIntegration) {
      assertThat(Files.readString(config)).contains("user");
    } else {
      assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
      assertThat(Files.readString(config)).isEqualTo("{\"user\": true}\n");
    }
  }

  @Test
  void agentsWithoutAPromptEventSupportNoPromptHook() {
    final List<AgentIntegration> others =
        List.of(
            new CopilotIntegration(),
            new AmpIntegration(),
            new ClineIntegration(),
            new JunieIntegration(),
            new WindsurfIntegration(),
            new OpenCodeIntegration()
        );

    assertThat(others).noneMatch(AgentIntegration::supportsPromptHook);
  }
}
