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

  @TempDir Path project;

  @Test
  void strictTypeScriptProjectGetsStrykerAndStrictConfigs()
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("typescript-project", project);

    final NodeFixtureRun.Result init = init(fixture);

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read(WARD_TOML))
        .contains(
            "version = 2",
            "[checks.tsc]",
            "command = \"npx vitest run --coverage\"",
            "[checks.stryker]\nenabled = true\nprofiles = [\"ci\"]\n"
                + "command = \"npx stryker run\"")
        .contains("[checks.coverage]", "minimum = 85", "minimum-branches = 75");
    assertThat(fixture.read("package.json"))
        .contains("\"@stryker-mutator/core\": \"10.1.1\"", "\"@stryker-mutator/vitest-runner\"");
    assertThat(fixture.read("ward-rules.toml"))
        .contains("preset = \"strict\"", "languages = [\"typescript\", \"swift\"]");
    assertThat(project.resolve("eslint.config.mjs")).doesNotExist();
    assertThat(fixture.read("vitest.config.mjs")).doesNotContain("threshold");
    assertThat(fixture.read(STRYKER_CONFIG)).contains("testRunner: \"vitest\"", "break: 60,");
    assertThat(fixture.read(GITIGNORE)).contains(".stryker-tmp/", "reports/mutation/");
    assertThat(fixture.read(AGENTS))
        .contains("ward-rules.toml", "85% line and 75% branch", "Stryker")
        .doesNotContain("JaCoCo", "PIT");

    final NodeFixtureRun.Result check = fixture.ward("check", "--profile", "ci");

    assertThat(check.exit()).as(check.output()).isZero();
  }

  @Test
  void strictJavaScriptProjectWithJestGetsTheJestRunner() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("javascript-commonjs-project", project);
    Files.writeString(
        project.resolve("package.json"),
        "{\n  \"name\": \"shop\",\n  \"private\": true,\n  \"scripts\": { \"test\": \"jest\" },\n"
            + "  \"devDependencies\": {\n"
            + "    \"jest\": \"^30.0.0\"\n  }\n}\n");

    final NodeFixtureRun.Result init = init(fixture);

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read(WARD_TOML))
        .contains("[checks.jest]", "command = \"npx jest --coverage\"", "[checks.stryker]")
        .doesNotContain("[checks.tsc]", "[checks.vitest]");
    assertThat(fixture.read("package.json"))
        .contains("\"@stryker-mutator/jest-runner\": \"10.1.1\"")
        .doesNotContain("typescript", "vitest");
    assertThat(project.resolve("jest.config.mjs")).doesNotExist();
    assertThat(fixture.read(WARD_TOML)).contains("minimum = 85", "minimum-branches = 75");
    assertThat(fixture.read(STRYKER_CONFIG)).contains("testRunner: \"jest\"", "break: 60,");
    assertThat(fixture.read("ward-rules.toml")).contains("languages = [\"javascript\", \"swift\"]");
    assertThat(project.resolve("eslint.config.mjs")).doesNotExist();
    assertThat(fixture.read(AGENTS)).contains("Tests (Jest)", "85% line").doesNotContain("tsc");

    final NodeFixtureRun.Result check = fixture.ward("check", "--profile", "pre-push");

    assertThat(check.exit()).as(check.output()).isZero();
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
