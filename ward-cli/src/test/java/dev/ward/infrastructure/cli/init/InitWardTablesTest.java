package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
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
 * The {@code ward} layer, cycle and independence tables that every init writes. TypeScript, Python
 * and Java, end to end on the offline fixtures.
 */
class InitWardTablesTest {

  @TempDir Path project;

  @Test
  void typeScriptStrictWritesTheCycleRootsAndNoExternalConfig()
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
            "--yes");

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read("ward.toml"))
        .contains("[[checks.ward.layers]]", "[checks.ward.cycles]", "roots = [\"src\"]")
        .doesNotContain("dependency-cruiser");
    assertThat(project.resolve(".dependency-cruiser.mjs")).doesNotExist();
  }

  @Test
  void pythonStrictWritesTheCycleAndIndependenceTables() throws IOException {
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
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(python.resolve(".importlinter")).doesNotExist();
    assertThat(Files.readString(python.resolve("ward.toml")))
        .contains("[checks.ward.cycles]", "[[checks.ward.independence]]")
        .doesNotContain("import-linter");
  }

  @Test
  void gradleStrictWritesTheCycleRootsAndNoArchUnitTest() throws IOException {
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
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    try (Stream<Path> files = Files.walk(project)) {
      assertThat(files.map(file -> file.getFileName().toString()))
          .doesNotContain("ArchitectureTest.java");
    }
    assertThat(Files.readString(project.resolve("ward.toml")))
        .contains("[checks.ward.cycles]", "roots = [\"src/main/java/shop\"]");
  }

  @Test
  void dryRunWritesNoWardTables() throws IOException {
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
            "--dry-run",
            "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(python.resolve("ward.toml")).doesNotExist();
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
