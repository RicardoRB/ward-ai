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

/** Java and Kotlin sources both get Ward's rules, and no external Checkstyle, PMD or SpotBugs. */
class InitKotlinPresetTest {
  @TempDir Path java;
  @TempDir Path kotlin;
  @TempDir Path mixed;

  @Test
  void javaSelectsOnlyJavaWardRules() throws IOException {
    final String toml = wardToml(java, "src/main/java");

    assertThat(toml)
        .contains("[checks.mutation]")
        .doesNotContain("[checks.checkstyle]", "[checks.pmd]", "[checks.spotbugs]");
    assertThat(java.resolve("ward-rules.toml")).content().contains("languages = [\"java\"]");
  }

  @Test
  void kotlinUsesJavaGatesWithoutExternalToolFiles() throws IOException {
    final String toml = wardToml(kotlin, "src/main/kotlin");

    assertThat(toml)
        .contains("[checks.mutation]", "[checks.coverage]")
        .doesNotContain("[checks.checkstyle]", "[checks.pmd]", "[checks.spotbugs]");
    assertThat(kotlin.resolve("ward-rules.toml"))
        .content()
        .contains("languages = [\"kotlin\"]")
        .doesNotContain("\"java\"");
    assertNoExternalToolFiles(kotlin);
    assertThat(Files.readString(kotlin.resolve("build.gradle.kts")))
        .doesNotContain("checkstyle", "pmd", "spotbugs");
  }

  @Test
  void mixedProjectListsJavaBeforeKotlin() throws IOException {
    wardToml(mixed, "src/main/java", "src/main/kotlin");

    assertThat(mixed.resolve("ward-rules.toml"))
        .content()
        .contains("languages = [\"java\", \"kotlin\"]");
    assertNoExternalToolFiles(mixed);
  }

  private static void assertNoExternalToolFiles(final Path root) {
    assertThat(root.resolve("config/checkstyle.xml")).doesNotExist();
    assertThat(root.resolve("config/pmd-ruleset.xml")).doesNotExist();
    assertThat(root.resolve("config/spotbugs-exclude.xml")).doesNotExist();
  }

  private static String wardToml(final Path root, final String... sources) throws IOException {
    Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }\n");
    for (final String source : sources) {
      Files.createDirectories(root.resolve(source));
    }
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
