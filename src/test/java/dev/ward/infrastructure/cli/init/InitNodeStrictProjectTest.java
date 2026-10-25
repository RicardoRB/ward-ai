package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code ward init --preset strict} on the offline TypeScript and JavaScript fixtures. */
class InitNodeStrictProjectTest {
  private static final String WARD_TOML = "ward.toml";
  private static final String STRYKER_CONFIG = "stryker.config.mjs";
  private static final String GITIGNORE = ".gitignore";
  private static final String AGENTS = "AGENTS.md";
  private static final String DEPENDENCY_CRUISER_CONFIG = ".dependency-cruiser.mjs";

  @TempDir Path project;

  @Test
  void strictTypeScriptProjectGetsStrykerAndStrictConfigs()
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("typescript-project", project);

    final NodeFixtureRun.Result init = init(fixture);

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read(WARD_TOML))
        .contains(
            "preset = \"strict\"",
            "[quality-gates.tsc]",
            "command = \"npx vitest run --coverage\"",
            "[quality-gates.stryker]\nenabled = true\nprofiles = [\"ci\"]\n"
                + "command = \"npx stryker run\"")
        .doesNotContain("minimum");
    assertThat(fixture.read("package.json"))
        .contains("\"@stryker-mutator/core\": \"10.1.1\"", "\"@stryker-mutator/vitest-runner\"");
    assertThat(fixture.read("eslint.config.mjs"))
        .startsWith("// managed-by: ward preset=strict\n")
        .contains("tseslint.configs.strictTypeChecked", "projectService: true");
    assertThat(fixture.read("vitest.config.mjs")).contains("lines: 85,", "branches: 75,");
    assertThat(fixture.read(STRYKER_CONFIG)).contains("testRunner: \"vitest\"", "break: 60,");
    assertThat(fixture.read(GITIGNORE)).contains(".stryker-tmp/", "reports/mutation/");
    assertThat(fixture.read(AGENTS))
        .contains("strict-type-checked", "85% line and 75% branch", "Stryker")
        .doesNotContain("JaCoCo", "PIT");

    final NodeFixtureRun.Result check = fixture.ward("check", "--profile", "ci");

    assertThat(check.exit()).as(check.output()).isZero();
  }

  @Test
  void strictJavaScriptProjectWithJestGetsTheJestRunner() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("javascript-commonjs-project", project);
    Files.writeString(
        project.resolve("package.json"),
        "{\n  \"name\": \"shop\",\n  \"private\": true,\n  \"devDependencies\": {\n"
            + "    \"jest\": \"^30.0.0\"\n  }\n}\n");

    final NodeFixtureRun.Result init = init(fixture);

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read(WARD_TOML))
        .contains(
            "[quality-gates.jest]", "command = \"npx jest --coverage\"", "[quality-gates.stryker]")
        .doesNotContain("[quality-gates.tsc]", "[quality-gates.vitest]");
    assertThat(fixture.read("package.json"))
        .contains("\"@stryker-mutator/jest-runner\": \"10.1.1\"")
        .doesNotContain("typescript", "vitest");
    assertThat(fixture.read("jest.config.mjs")).contains("lines: 85,", "branches: 75,");
    assertThat(fixture.read(STRYKER_CONFIG)).contains("testRunner: \"jest\"", "break: 60,");
    assertThat(fixture.read("eslint.config.mjs"))
        .contains("js.configs.recommended", "globals.jest")
        .doesNotContain("typescript-eslint");
    assertThat(fixture.read(AGENTS)).contains("Tests (Jest)", "85% line").doesNotContain("tsc");

    final NodeFixtureRun.Result check = fixture.ward("check", "--profile", "pre-push");

    assertThat(check.exit()).as(check.output()).isZero();
  }

  @Test
  void strictTypeScriptProjectGetsALayeredDependencyCruiserConfig()
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("typescript-project", project);

    final NodeFixtureRun.Result init = init(fixture);

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(init.output()).contains("Generated layered dependency-cruiser config");
    assertThat(fixture.read(WARD_TOML))
        .contains(
            "[quality-gates.dependency-cruiser]",
            "command = \"npx depcruise . --config .dependency-cruiser.mjs --output-type err\"");
    assertThat(fixture.read("package.json")).contains("\"dependency-cruiser\": \"18.6.0\"");
    assertThat(fixture.read(DEPENDENCY_CRUISER_CONFIG))
        .startsWith("// managed-by: ward\n")
        .contains("name: \"layered-architecture\"", "name: \"no-circular\"");
    assertThat(fixture.read(AGENTS)).contains("Dependency rules (dependency-cruiser)");
  }

  @Test
  void projectsOwnDependencyCruiserConfigIsKeptAndPointedAt()
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("typescript-project", project);
    Files.writeString(project.resolve(".dependency-cruiser.cjs"), "module.exports = {};\n");

    final NodeFixtureRun.Result init = init(fixture);

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read(".dependency-cruiser.cjs")).isEqualTo("module.exports = {};\n");
    assertThat(project.resolve(DEPENDENCY_CRUISER_CONFIG)).doesNotExist();
    assertThat(fixture.read(WARD_TOML))
        .contains(
            "command = \"npx depcruise . --config .dependency-cruiser.cjs --output-type err\"");
  }

  @Test
  void unsupportedArchitectureStyleFailsBeforeAnyFileIsWritten()
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("typescript-project", project);

    final NodeFixtureRun.Result init =
        fixture.ward("init", "--preset", "strict", "--architecture", "onion", "--yes");

    assertThat(init.exit()).isNotZero();
    assertThat(init.output()).contains("layered, hexagonal", "clean");
    assertThat(project.resolve(WARD_TOML)).doesNotExist();
    assertThat(project.resolve(DEPENDENCY_CRUISER_CONFIG)).doesNotExist();
  }

  private static NodeFixtureRun.Result init(final NodeFixtureRun fixture)
      throws IOException, InterruptedException {
    return fixture.ward(
        "init",
        "--preset",
        "strict",
        "--integration",
        "none",
        "--ci",
        "github",
        "--agent-rules",
        "--yes");
  }
}
