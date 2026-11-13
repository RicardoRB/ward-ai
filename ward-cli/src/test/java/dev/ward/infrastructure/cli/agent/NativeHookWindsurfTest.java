package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.agent.WindsurfIntegration;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins Windsurf delivery: pre-hooks refuse with exit 2 and stderr, which Cascade shows the agent.
 */
class NativeHookWindsurfTest {
  @TempDir Path root;

  private NativeHookRun hook;

  @BeforeEach
  void project() throws IOException {
    hook = new NativeHookRun(root, new WindsurfIntegration());
  }

  private String event(final String name, final String toolInfo) {
    return "{\"agent_action_name\":\""
        + name
        + "\",\"trajectory_id\":\"t\",\"execution_id\":\"e\",\"timestamp\":\"2026-10-16T00:00:00Z\","
        + "\"model_name\":\"m\",\"tool_info\":"
        + toolInfo
        + "}";
  }

  @Test
  void failingChecksAfterAWriteAreReportedOnStderr() {
    assertThat(
            hook.run(
                1,
                event("post_write_code", "{\"file_path\":\"" + root.resolve("src/A.java") + "\"}")))
        .isEqualTo(2);
    assertThat(hook.err.toString()).contains("Ward quality findings");
  }
}
