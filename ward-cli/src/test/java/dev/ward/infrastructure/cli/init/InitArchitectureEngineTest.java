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

/** {@code --architecture-engine}: the ward engine writes the boundaries, and the tool is kept. */
class InitArchitectureEngineTest {

  @TempDir Path root;

  @Test
  void wardEngineWritesTheBoundariesAndGeneratesNoLayerTest() throws IOException {
    mavenProject();
    final InitCommandFixture init = init();

    final int exit =
        init.run(
            "",
            "--preset",
            "standard",
            "--integration",
            "none",
            "--architecture",
            "hexagonal",
            "--architecture-engine",
            "ward",
            "--gate",
            "archunit",
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    final String toml = Files.readString(root.resolve("ward.toml"));
    assertThat(toml)
        .contains("[checks.ward]", "uses = \"ward\"", "[[checks.ward.layers]]")
        .contains("from = [\"domain\", \"application\"]")
        .contains("forbidden = [\"adapter\", \"infrastructure\"]")
        .doesNotContain("[checks.archunit]");
    assertThat(root.resolve("src/test/java/ArchitectureTest.java")).doesNotExist();
  }

  @Test
  void anUnknownEngineIsRejectedBeforeAnyChange() throws IOException {
    mavenProject();
    final InitCommandFixture init = init();

    final int exit =
        init.run(
            "",
            "--preset",
            "standard",
            "--integration",
            "none",
            "--architecture-engine",
            "other",
            "--yes");

    assertThat(exit).isNotEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("Supported engines: tool, ward");
    assertThat(root.resolve("ward.toml")).doesNotExist();
  }

  @Test
  void aKotlinProjectIsRefusedForTheWardEngineNamingTheLanguage() throws IOException {
    Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }\n");
    Files.createDirectories(root.resolve("src/main/kotlin"));
    Files.writeString(root.resolve("src/main/kotlin/App.kt"), "class App\n");
    final InitCommandFixture init = init();

    final int exit =
        init.run(
            "",
            "--preset",
            "standard",
            "--integration",
            "none",
            "--architecture",
            "layered",
            "--architecture-engine",
            "ward",
            "--yes");

    assertThat(exit).isNotEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("cannot check Kotlin");
    assertThat(root.resolve("ward.toml")).doesNotExist();
  }

  private void mavenProject() throws IOException {
    Files.writeString(
        root.resolve("pom.xml"),
        "<project><modelVersion>4.0.0</modelVersion><groupId>g</groupId>"
            + "<artifactId>a</artifactId><version>1</version></project>\n");
    Files.createDirectories(root.resolve("src/main/java/shop"));
    Files.writeString(root.resolve("src/main/java/shop/App.java"), "package shop;\nclass App {}\n");
  }

  private InitCommandFixture init() throws IOException {
    return InitCommandFixture.repository(
        root,
        new JvmBuildToolConfigurator(new PomToolConfigurator(), new GradleToolConfigurator()));
  }
}
