package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code vulnerabilities} gate is offered, never part of a preset, and needs no install. */
class InitVulnerabilitiesTest {
  private static final String POM =
      "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>"
          + "<artifactId>demo</artifactId><version>1.0.0</version></project>";
  private static final String WARD_TOML = "ward.toml";
  private static final String GATE = "vulnerabilities";

  @TempDir Path root;

  private InitCommandFixture mavenProject() throws IOException {
    final InitCommandFixture init = InitCommandFixture.repository(root);
    Files.writeString(root.resolve("pom.xml"), POM);
    return init;
  }

  @Test
  void aPresetDoesNotEnableTheGate() throws IOException {
    final InitCommandFixture init = mavenProject();

    final int exit = init.run("", "--integration", "none", "--preset", "standard", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve(WARD_TOML))).doesNotContain(GATE);
  }

  @Test
  void selectingTheGateWritesTheNativeCheckInTheCiProfile() throws IOException {
    final InitCommandFixture init = mavenProject();

    final int exit = init.run("", "--integration", "none", "--gate", GATE, "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    final String toml = Files.readString(root.resolve(WARD_TOML));
    assertThat(toml)
        .contains("[checks.vulnerabilities]")
        .contains("uses = \"vulnerabilities\"")
        .contains("profiles = [\"ci\"]");
    final String section = toml.substring(toml.indexOf("[checks.vulnerabilities]"));
    assertThat(section).doesNotContain("command =");
    assertThat(init.output()).doesNotContain("Install ").doesNotContain("--install");
  }
}
