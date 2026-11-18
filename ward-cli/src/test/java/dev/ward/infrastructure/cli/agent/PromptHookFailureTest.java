package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.PromptScanner;
import dev.ward.domain.config.WardException;
import dev.ward.infrastructure.agent.CodexIntegration;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A scan that cannot run blocks the prompt the same way, whatever the cause. */
class PromptHookFailureTest {
  private static final String PROMPT = "{\"hook_event_name\":\"UserPromptSubmit\",\"prompt\":\"my-private-words\"}";

  @TempDir Path root;

  private PromptHookRun failing(final RuntimeException failure) {
    final PromptScanner scanner =
        (directory, prompt) -> {
          throw failure;
        };
    return new PromptHookRun(root, scanner);
  }

  @Test
  @DisplayName("a scan that cannot run blocks the prompt")
  void aKnownFailureBlocksWithItsReason() {
    // Given a broken rule file, reported as a Ward error
    final PromptHookRun run = failing(new WardException("rule file is broken"));

    // When I submit a prompt
    final int exit = run.nativeHook(new CodexIntegration(), PROMPT);

    // Then it is blocked and says the scan could not run, with no prompt content
    assertThat(exit).isZero();
    assertThat(run.out.toString())
        .contains("\"decision\":\"block\"", "could not scan this prompt", "rule file is broken")
        .doesNotContain("my-private-words");
  }

  @Test
  @DisplayName("an unexpected error blocks the prompt the same way")
  void anUnexpectedErrorBlocksToo() {
    // Given an unexpected failure inside the scan
    final PromptHookRun run = failing(new IllegalStateException("secret-looking-detail"));

    // When I submit a prompt through Codex and through Claude Code
    final int codex = run.nativeHook(new CodexIntegration(), PROMPT);
    final String nativeOut = run.out.toString();
    final int claude = run.claude(PROMPT);

    // Then both block with the generic message, without the cause or any prompt content
    assertThat(codex).isZero();
    assertThat(claude).isZero();
    assertThat(nativeOut).contains("\"decision\":\"block\"", "unexpected error");
    assertThat(run.out.toString()).contains("\"decision\":\"block\"", "unexpected error");
    assertThat(nativeOut + run.out)
        .doesNotContain("secret-looking-detail")
        .doesNotContain("my-private-words");
  }
}
