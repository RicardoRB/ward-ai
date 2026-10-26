package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.agent.JunieIntegration;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins Junie CLI delivery: PreToolUse denial for approved artifacts, and Stop retry with findings.
 */
class NativeHookJunieTest {
  private static final String STOP = "{\"hook_event_name\":\"Stop\",\"stop_hook_active\":false}";
  private static final String STOP_CONTINUED =
      "{\"hook_event_name\":\"Stop\",\"stop_hook_active\":true}";

  @TempDir Path root;

  private NativeHookRun hook;

  @BeforeEach
  void project() throws IOException {
    hook = new NativeHookRun(root, new JunieIntegration());
  }

  private static String preTool(final String name, final String input) {
    return "{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\""
        + name
        + "\",\"tool_input\":"
        + input
        + "}";
  }

  @Test
  void theGuardDeniesAWriteToAnApprovedSpec() {
    assertThat(
            hook.run(
                0,
                preTool(
                    "Write", "{\"file_path\":\"openspec/specs/auth/spec.md\",\"content\":\"x\"}"),
                "--guard-approved-specs"))
        .isEqualTo(2);
    assertThat(hook.out.toString()).isEmpty();
    assertThat(hook.err.toString()).contains("openspec/specs/auth/spec.md");
  }

  @Test
  void theGuardDeniesAPatchToAnApprovedSpec() {
    assertThat(
            hook.run(
                0,
                preTool(
                    "apply_patch",
                    "{\"patch\":\"*** Begin Patch\\n*** Update File: openspec/specs/auth/spec.md\\n@@\\n-a\\n+b\\n*** End Patch\"}"),
                "--guard-approved-specs"))
        .isEqualTo(2);
    assertThat(hook.out.toString()).isEmpty();
    assertThat(hook.err.toString()).contains("openspec/specs/auth/spec.md");
  }

  @Test
  void anAllowedCommandRunsNoGate() {
    assertThat(hook.run(1, preTool("Bash", "{\"command\":\"ls\"}"), "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).isEmpty();
    assertThat(hook.err.toString()).isEmpty();
  }

  @Test
  void failingChecksAtStopRetryTheTurnWithFindings() {
    assertThat(hook.run(1, STOP)).isZero();
    assertThat(hook.out.toString()).startsWith("{\"decision\":\"block\",\"reason\":");
  }

  @Test
  void aStopAlreadyContinuedByTheHookIsNotBlockedAgain() {
    assertThat(hook.run(1, STOP_CONTINUED)).isZero();
    assertThat(hook.out.toString()).isEmpty();
    assertThat(hook.err.toString()).contains("Ward quality findings");
  }

  @Test
  void aCleanTurnEndIsSilent() {
    assertThat(hook.run(0, STOP)).isZero();
    assertThat(hook.out.toString()).isEmpty();
  }
}
