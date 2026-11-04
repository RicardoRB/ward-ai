package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.init.ArchitectureLayers;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.JvmBuildToolConfigurator;
import dev.ward.infrastructure.init.gradle.GradleToolConfigurator;
import dev.ward.infrastructure.init.maven.PomToolConfigurator;
import dev.ward.infrastructure.init.python.PythonBuildToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code --architecture-engine ward} with strict extras: the engine checks the layers, and the
 * external tool keeps only the extras. TypeScript, end to end on the offline fixture.
 */
class InitWardExtrasTest {
  private static final String DEPENDENCY_CRUISER_CONFIG = ".dependency-cruiser.mjs";

  @TempDir Path project;

  @Test
  void typeScriptStrictWardKeepsOnlyNoCircularInTheDependencyCruiserConfig()
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("typescript-project", project);

    final NodeFixtureRun.Result init =
        fixture.ward(
            "init",
            "--preset",
            "strict",
            "--integration",
            "none",
            "--architecture",
            "hexagonal",
            "--architecture-engine",
            "ward",
            "--gate",
            "dependency-cruiser",
            "--yes");

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read("ward.toml")).contains("[[checks.ward.layers]]");
    assertThat(fixture.read(DEPENDENCY_CRUISER_CONFIG))
        .contains("no-circular")
        .doesNotContain("hexagonal-architecture");
  }

  @Test
  void pythonStrictWardKeepsOnlyTheIndependenceContract() throws IOException {
    final Path python = Files.createDirectories(project.resolve("py"));
    writeUvProject(python);
    final InitCommandFixture init =
        InitCommandFixture.repository(
            python, new PythonBuildToolConfigurator(new FakeCommandExecutor(0, "", "")), "uv");

    final int exit =
        init.run(
            "",
            "--preset",
            "strict",
            "--integration",
            "none",
            "--architecture",
            "layered",
            "--architecture-engine",
            "ward",
            "--gate",
            "import-linter",
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(python.resolve(".importlinter")))
        .contains("type = independence")
        .doesNotContain("layers");
  }

  @Test
  void gradleStrictWardKeepsTheExtrasInTheArchUnitTestAndDropsTheLayerRule() throws IOException {
    Files.writeString(project.resolve("build.gradle.kts"), "plugins { java }\n");
    Files.createDirectories(project.resolve("src/main/java/shop"));
    Files.writeString(
        project.resolve("src/main/java/shop/App.java"), "package shop;\nclass App {}\n");
    final InitCommandFixture init =
        InitCommandFixture.repository(
            project,
            new JvmBuildToolConfigurator(new PomToolConfigurator(), new GradleToolConfigurator()));

    final int exit =
        init.run(
            "",
            "--preset",
            "strict",
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
    final String archUnit;
    try (Stream<Path> files = Files.walk(project.resolve("src/test/java"))) {
      archUnit =
          Files.readString(
              files
                  .filter(file -> "ArchitectureTest.java".equals(file.getFileName().toString()))
                  .findFirst()
                  .orElseThrow());
    }
    assertThat(archUnit).contains("noCycles").doesNotContain("..domain..", "..adapter..");
  }

  @Test
  void dryRunNamesTheExtrasOnlyToolPlanAndWritesNothing() throws IOException {
    final Path python = Files.createDirectories(project.resolve("py"));
    writeUvProject(python);
    final InitCommandFixture init =
        InitCommandFixture.repository(
            python, new PythonBuildToolConfigurator(new FakeCommandExecutor(0, "", "")), "uv");

    final int exit =
        init.run(
            "",
            "--preset",
            "strict",
            "--integration",
            "none",
            "--architecture",
            "layered",
            "--architecture-engine",
            "ward",
            "--gate",
            "import-linter",
            "--dry-run",
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains(ArchitectureLayers.EXTRAS_ONLY_NOTE);
    assertThat(python.resolve(".importlinter")).doesNotExist();
  }

  private static void writeUvProject(final Path root) throws IOException {
    Files.writeString(
        root.resolve("pyproject.toml"),
        "[project]\nname = \"shop\"\nversion = \"0.1.0\"\nrequires-python = \">=3.11\"\ndependencies = []\n");
    Files.writeString(root.resolve("uv.lock"), "version = 1\n");
    Files.createDirectories(root.resolve("src/shop"));
    Files.writeString(root.resolve("src/shop/__init__.py"), "");
    for (final String feature : new String[] {"orders", "billing"}) {
      Files.createDirectories(root.resolve("src/shop").resolve(feature));
      Files.writeString(root.resolve("src/shop").resolve(feature).resolve("__init__.py"), "");
    }
  }
}
