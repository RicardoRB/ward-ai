package dev.ward.infrastructure.cli.agent;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.agent.CodexIntegration;
import dev.ward.infrastructure.agent.CursorIntegration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The prompt hook of every supported agent, through the real hook command and secret rules: one
 * blocked prompt with a fake secret from the {@code rules/secrets} fixtures, one clean prompt.
 */
class PromptHookTest {
  private static final String RULE = "ward:secrets/github-pat";
  private static final String CONFIG =
      "version = 2\n[checks.secrets]\nuses = \"secrets\"\n[checks.secrets.prompts]\nenabled = true\n";

  @TempDir Path root;

  private PromptHookRun hook;

  @BeforeEach
  void project() throws IOException {
    withPom(root, PLAIN_POM);
    Files.writeString(root.resolve("ward.toml"), CONFIG);
    hook = new PromptHookRun(root);
  }

  static Stream<Arguments> agents() {
    return Stream.of(
        Arguments.of("codex", "UserPromptSubmit", "\"decision\":\"block\""),
        Arguments.of("factory-droid", "UserPromptSubmit", "\"decision\":\"block\""),
        Arguments.of("gemini", "BeforeAgent", "\"decision\":\"deny\""),
        Arguments.of("cursor", "beforeSubmitPrompt", "\"continue\":false")
    );
  }

  @ParameterizedTest
  @MethodSource("agents")
  @DisplayName("a prompt with a secret")
  void aPromptWithASecret(final String id, final String event, final String blocks)
      throws IOException {
    // Given an agent with Ward's prompt hook installed
    final String secret = PromptHookRun.secret();

    // When I submit a prompt that contains a fake secret from the rules/secrets fixtures
    final int exit = hook.nativeHook(
        PromptHookRun.adapter(id),
        PromptHookRun.payload(event, "use\n" + secret)
    );

    // Then the prompt is rejected the agent's way, the message names the rule, line and column,
    // and the secret appears in no output
    assertThat(exit).isZero();
    assertThat(hook.out.toString()).contains(blocks).contains(RULE).contains("line 2, column 1");
    assertThat(hook.out.toString() + hook.err).doesNotContain(secret);
  }

  @Test
  @DisplayName("a prompt with a secret")
  void aPromptWithASecretInClaudeCode() throws IOException {
    // Given Claude Code with Ward's prompt hook installed
    final String secret = PromptHookRun.secret();

    // When I submit a prompt that contains a fake secret from the rules/secrets fixtures
    final int exit = hook.claude(PromptHookRun.payload("UserPromptSubmit", "use\n" + secret));

    // Then the prompt is blocked with decision block, naming the rule, line and column
    assertThat(exit).isZero();
    assertThat(hook.out.toString())
        .contains("\"decision\":\"block\"")
        .contains(RULE)
        .contains("line 2, column 1");
    assertThat(hook.out.toString() + hook.err).doesNotContain(secret);
  }

  @ParameterizedTest
  @MethodSource("agents")
  @DisplayName("a clean prompt")
  void aCleanPrompt(final String id, final String event, final String blocks) {
    // Given the same agent
    // When I submit a prompt without secrets
    final int exit = hook.nativeHook(
        PromptHookRun.adapter(id),
        PromptHookRun.payload(event, "Rename the class Foo.")
    );

    // Then it passes untouched: no output
    assertThat(exit).isZero();
    assertThat(hook.out.toString()).isEmpty();
    assertThat(hook.err.toString()).isEmpty();
  }

  @Test
  @DisplayName("a clean prompt")
  void aCleanPromptInClaudeCode() {
    // Given Claude Code with the prompt hook
    // When I submit a prompt without secrets
    final int exit = hook.claude(
        PromptHookRun.payload("UserPromptSubmit", "Rename the class Foo.")
    );

    // Then it passes untouched
    assertThat(exit).isZero();
    assertThat(hook.out.toString()).isEmpty();
  }

  @Test
  @DisplayName("a file attached to the prompt")
  void aFileAttachedToTheCursorPrompt() throws IOException {
    // Given a Cursor prompt that attaches a file containing a fake secret
    Files.writeString(root.resolve("notes.txt"), "a\ntoken: " + PromptHookRun.secret() + "\n");
    final String payload =
        "{\"hook_event_name\":\"beforeSubmitPrompt\",\"prompt\":\"read it\","
            + "\"attachments\":[{\"type\":\"file\",\"file_path\":"
            + PromptHookRun.quote(root.resolve("notes.txt").toString())
            + "}]}";

    // When I submit it
    final int exit = hook.nativeHook(new CursorIntegration(), payload);

    // Then it is blocked, naming the file, the rule, the line and the column
    assertThat(exit).isZero();
    assertThat(hook.out.toString()).contains("notes.txt, line 2, column 8").contains(RULE);
  }

  @Test
  @DisplayName("prompt scanning is configured in its own table")
  void withoutTheTableThePromptIsNotScanned() throws IOException {
    // Given ward.toml without [checks.secrets.prompts]
    Files.writeString(
        root.resolve("ward.toml"),
        "version = 2\n[checks.secrets]\nuses = \"secrets\"\n"
    );

    // When I submit a prompt with a fake secret
    final int exit = hook.nativeHook(
        new CodexIntegration(),
        PromptHookRun.payload("UserPromptSubmit", PromptHookRun.secret())
    );

    // Then the hook does not scan it
    assertThat(exit).isZero();
    assertThat(hook.out.toString()).isEmpty();
  }
}
