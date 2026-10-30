package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectSnapshot;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.init.NodeBuildToolConfigurator;
import dev.ward.infrastructure.init.PythonBuildToolConfigurator;
import dev.ward.infrastructure.project.FileSystemPackageManifests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Init on a uv project plans the ward group, the owned configs and the install, changing nothing.
 */
class PythonInitCliTest {
  private static final String PYPROJECT =
      "[project]\nname = \"shop\"\nversion = \"0.1.0\"\nrequires-python = \">=3.11\"\ndependencies = []\n";

  @TempDir Path root;

  @Test
  void dryRunPlansTheWardGroupAndOwnedConfigsWithoutChangingFiles() throws IOException {
    writeUvProject();
    final InitCommandFixture init = python();
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit =
        init.run("", "--preset", "standard", "--integration", "none", "--yes", "--dry-run");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains(
            "add ruff==0.17.1 to the ward group",
            "run uv sync --group ward",
            "create     ruff.toml")
        .contains("create     .coveragerc")
        .doesNotContain("pom.xml");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  void strictApplyWritesStrictConfigsAndTheMutationGate() throws IOException {
    writeUvProject();
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final InitCommandFixture init =
        InitCommandFixture.repository(root, new PythonBuildToolConfigurator(executor), "uv");

    final int exit = init.run("", "--preset", "strict", "--integration", "none", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("ruff.toml")))
        .contains("\"C90\"", "max-complexity = 10");
    assertThat(Files.readString(root.resolve("mypy.ini"))).contains("strict = True");
    assertThat(Files.readString(root.resolve(".coveragerc")))
        .contains("branch = True", "fail_under = 85");
    assertThat(Files.readString(root.resolve("ward.toml")))
        .contains("[checks.mutmut]", "command = \"uv run --group ward mutmut run\"")
        .contains("minimum = 60");
    assertThat(Files.readString(root.resolve("pyproject.toml"))).contains("mutmut==3.8.0");
  }

  @Test
  void applyWritesConfigsIgnoreRulesAndAgentRulesAndInstallsTheGroup() throws IOException {
    writeUvProject();
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final InitCommandFixture init =
        InitCommandFixture.repository(root, new PythonBuildToolConfigurator(executor), "uv");

    final int exit =
        init.run("", "--preset", "standard", "--integration", "none", "--yes", "--agent-rules");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve("pyproject.toml")))
        .contains("[dependency-groups]", "ruff==0.17.1");
    assertThat(Files.readString(root.resolve(".gitignore")))
        .contains(".ward/reports/", "mutants/", ".coverage");
    assertThat(Files.readString(root.resolve("AGENTS.md")))
        .contains("# noqa", "# type: ignore", "per-file-ignores")
        .contains("80%");
    assertThat(executor.commands).containsExactly(List.of("uv", "sync", "--group", "ward"));
  }

  @Test
  void sharedPythonAndNodeDirectoryConfiguresNodeAndNotesThePythonProject() throws IOException {
    writeUvProject();
    Files.writeString(root.resolve("package.json"), "{\n  \"name\": \"web\"\n}\n");
    Files.writeString(root.resolve("tsconfig.json"), "{}\n");
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/index.ts"), "export const x = 1;\n");
    final InitCommandFixture init =
        InitCommandFixture.repository(
            root,
            new NodeBuildToolConfigurator(
                new FileSystemPackageManifests(), new FakeCommandExecutor(0, "", "")),
            "npm");

    final int exit = init.run("", "--preset", "standard", "--integration", "none", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("A Python project shares this directory with the Node project")
        .doesNotContain("uv sync");
  }

  private void writeUvProject() throws IOException {
    Files.writeString(root.resolve("pyproject.toml"), PYPROJECT);
    Files.writeString(root.resolve("uv.lock"), "version = 1\n");
  }

  private InitCommandFixture python() throws IOException {
    return InitCommandFixture.repository(
        root, new PythonBuildToolConfigurator(new FakeCommandExecutor(0, "", "")), "uv");
  }
}
