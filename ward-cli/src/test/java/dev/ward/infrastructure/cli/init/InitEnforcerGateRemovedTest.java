package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init knows no {@code enforcer} gate and writes no Enforcer plugin or rules file. */
class InitEnforcerGateRemovedTest {
  @TempDir Path root;

  @Test
  void theEnforcerGateIsUnknownAndExitsWithTwo() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run("", "--integration", "none", "--gate", "enforcer");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains("enforcer");
  }

  @Test
  void strictWritesTheNativeRulesInsteadOfTheEnforcerFiles() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run("", "--yes", "--preset", "strict", "--integration", "none");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.configuration()).doesNotContain("enforcer");
    assertThat(root.resolve("config/enforcer-rules.xml")).doesNotExist();
    assertThat(Files.readString(root.resolve("pom.xml"))).doesNotContain("maven-enforcer-plugin");
    assertThat(Files.readString(root.resolve("ward-rules.toml")))
        .contains("\"build.dependency-convergence\"", "\"build.require-upper-bound\"");
  }
}
