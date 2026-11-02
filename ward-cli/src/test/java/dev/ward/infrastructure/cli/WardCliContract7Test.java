package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
class WardCliContract7Test extends WardCliIntegrationSupport {
  @Test
  void hookPassingWithoutPromptsEmitsNothing() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "format", "-C", project.toString());
    out.getBuffer().setLength(0);
    err.getBuffer().setLength(0);

    assertThat(run("hook", CLAUDE_CODE, LEARN_AFTER, "1", "-C", project.toString()))
        .isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).doesNotContain("FAILED");
  }

  @Test
  void hookOnProjectWithoutConfigurationExitsTwo() {
    out.getBuffer().setLength(0);
    err.getBuffer().setLength(0);

    assertThat(run("hook", CLAUDE_CODE, "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("ward:", "ward init");
    assertThat(out.toString()).isEmpty();
  }
}
