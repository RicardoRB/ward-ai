package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.agent.JunieIntegration;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins Junie CLI delivery: Stop retry with findings. */
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
