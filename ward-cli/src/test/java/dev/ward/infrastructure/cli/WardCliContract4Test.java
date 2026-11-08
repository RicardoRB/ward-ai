package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
class WardCliContract4Test extends WardCliIntegrationSupport {
  @Test
  void initInstallsMultipleIntegrationsSelectedWithSpaces() {
    assertThat(runInitWithInput("3\n2 3\n1\n", "-C", project.toString())).isZero();
    assertThat(project.resolve(".opencode/commands/ward-check.md")).exists();
    assertThat(project.resolve(".opencode/plugins/ward-edit-write.js")).exists();
    assertThat(project.resolve(".claude/settings.json")).exists();
    assertThat(out.toString())
        .contains("Created Ward-owned OpenCode", "Created Ward-owned Claude Code");
  }

  @Test
  void initConfiguresMultipleQualityGatesSelectedWithSpaces() throws IOException {
    assertThat(runInitWithInput("3\n1\n1 2\n", "-C", project.toString())).isZero();
    final String config = Files.readString(project.resolve(WARD_TOML));
    assertThat(config).contains("[checks.tests]", "[checks.compile]");
  }

  @Test
  void selectedCoverageAddsMissingMavenPluginAndReportsIt() throws IOException {
    assertThat(
            run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "coverage", "-C", project.toString()))
        .isZero();
    assertThat(Files.readString(project.resolve("pom.xml"))).contains("jacoco-maven-plugin");
    assertThat(out.toString()).contains("Updated pom.xml with Maven tools", "jacoco-maven-plugin");
  }

  @Test
  void existingMavenPluginIsNotDuplicated() throws IOException {
    final Path pom = project.resolve("pom.xml");
    final String original = Files.readString(pom);
    Files.writeString(
        pom,
        original.replace(
            "</project>",
            """
                <build><plugins><plugin><artifactId>jacoco-maven-plugin</artifactId></plugin></plugins></build>
                </project>"""));

    assertThat(
            run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "coverage", "-C", project.toString()))
        .isZero();
    final String updated = Files.readString(pom);
    assertThat(updated.indexOf("<artifactId>jacoco-maven-plugin</artifactId>"))
        .isEqualTo(updated.lastIndexOf("<artifactId>jacoco-maven-plugin</artifactId>"));
    assertThat(out.toString()).doesNotContain("Updated pom.xml with Maven tools");
  }

  @Test
  void generatedMavenAndArchitectureFilesRollBackOnLaterSetupFailure() throws IOException {
    final Path testRoot = project.resolve("src/test/java");
    Files.createDirectories(testRoot.getParent());
    Files.writeString(testRoot, "not a directory\n");
    final String originalPom = Files.readString(project.resolve("pom.xml"));

    assertThat(
            run(
                INIT,
                INTEGRATION_OPTION,
                NONE,
                GATE_OPTION,
                "archunit",
                "--architecture",
                "clean",
                "-C",
                project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(Files.readString(project.resolve("pom.xml"))).isEqualTo(originalPom);
    assertThat(project.resolve(WARD_TOML)).doesNotExist();
  }
}
