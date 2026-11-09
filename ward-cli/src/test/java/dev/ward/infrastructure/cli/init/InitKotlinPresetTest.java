package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.JvmBuildToolConfigurator;
import dev.ward.infrastructure.init.gradle.GradleToolConfigurator;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Java selects Ward's rules; Kotlin keeps its external analysis tools until its rules land. */
class InitKotlinPresetTest {
  @TempDir Path java;
  @TempDir Path kotlin;

  @Test
  void kotlinKeepsTheExternalToolsThatJavaDropsForWardRules() throws IOException {
    final String javaToml = wardToml(java, "src/main/java");
    final String kotlinToml = wardToml(kotlin, "src/main/kotlin");

    assertThat(javaToml)
        .contains("[checks.mutation]")
        .doesNotContain("[checks.checkstyle]", "[checks.pmd]", "[checks.spotbugs]");
    assertThat(kotlinToml)
        .contains("[checks.checkstyle]", "[checks.pmd]", "[checks.spotbugs]", "[checks.mutation]");
    assertThat(java.resolve("ward-rules.toml")).content().contains("languages = [\"java\", \"swift\"]");
  }

  private static String wardToml(final Path root, final String sources) throws IOException {
    Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }\n");
    Files.createDirectories(root.resolve(sources));
    final InitCommandFixture init =
        InitCommandFixture.repository(
            root,
            new JvmBuildToolConfigurator(new PomToolConfigurator(), new GradleToolConfigurator()));

    final int exit =
        init.run(
            "",
            "--preset",
            "strict",
            "--integration",
            "none",
            "--architecture",
            "layered",
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    return Files.readString(root.resolve("ward.toml"));
  }
}
