package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init rejects the removed {@code semgrep} gate like any unknown gate. */
class InitRemovedSemgrepGateTest {
  @TempDir Path root;

  @Test
  void theSemgrepGateIsUnknownAndExitsWithTwo() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run("", "--dry-run", "--integration", "none", "--gate", "semgrep");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains("Unknown quality gate 'semgrep'");
  }
}
