package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaudeCodeHookStopTest extends ClaudeCodeHookTestSupport {
  @Test
  void stopBlocksOnceWithTheQualityFindings() {
    testsPass = false;

    assertThat(hook(stop(false))).isEqualTo(ExitCodes.OK);

    final JsonTree output = JsonTree.parse(out.toString());
    assertThat(output.get("decision").asString()).isEqualTo("block");
    assertThat(output.get("reason").asString()).contains("tests: ");
    assertThat(commands).containsExactly(List.of("mvn", "test"));
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void stopAlreadyContinuedReportsToTheUserWithoutBlocking() {
    testsPass = false;

    assertThat(hook(stop(true))).isEqualTo(ExitCodes.OK);

    final JsonTree output = JsonTree.parse(out.toString());
    assertThat(output.get("systemMessage").asString()).contains("tests: ");
    assertThat(out.toString()).doesNotContain("decision");
  }

  @Test
  void cleanStopPrintsNothing() {
    assertThat(hook(stop(false))).isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).isEmpty();
    assertThat(commands).containsExactly(List.of("mvn", "test"));
  }

  @Test
  void stopWardErrorIsShownToTheUserAndNeverBlocks() throws IOException {
    Files.writeString(root.resolve("ward.toml"), "version = [");

    assertThat(hook(stop(false))).isEqualTo(ExitCodes.OK);

    final JsonTree output = JsonTree.parse(out.toString());
    assertThat(output.get("systemMessage").asString()).startsWith("ward: ");
    assertThat(out.toString()).doesNotContain("block");
  }
}
