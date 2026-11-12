package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.agent.ClineIntegration;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins Cline v3 delivery: PostToolUse contextModification. */
class NativeHookClineTest {
  @TempDir Path root;

  private NativeHookRun hook;

  @BeforeEach
  void project() throws IOException {
    hook = new NativeHookRun(root, new ClineIntegration());
  }

  private String pre(final String tool, final String parameters) {
    return "{\"clineVersion\":\"3.0.72\",\"hookName\":\"tool_call\",\"workspaceRoots\":[\""
        + root
        + "\"],\"tool_call\":{\"id\":\"1\",\"name\":\""
        + tool
        + "\",\"input\":{}},\"preToolUse\":{\"toolName\":\""
        + tool
        + "\",\"parameters\":"
        + parameters
        + "}}";
  }

  @Test
  void failingChecksAfterAnEditReachTheAgentAsContextModification() {
    final String payload =
        "{\"hookName\":\"tool_result\",\"postToolUse\":{\"toolName\":\"editor\","
            + "\"parameters\":{\"path\":\"src/A.java\"},\"result\":\"ok\",\"success\":true}}";
    assertThat(hook.run(1, payload)).isZero();
    assertThat(hook.out.toString())
        .startsWith("{\"contextModification\":")
        .contains("Ward quality findings");
  }
}
