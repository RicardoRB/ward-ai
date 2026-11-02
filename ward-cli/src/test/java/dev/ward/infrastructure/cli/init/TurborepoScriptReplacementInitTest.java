package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Turborepo script replacement question and its options, on a create-turbo-style workspace. */
class TurborepoScriptReplacementInitTest {
  private static final String FIXTURE = "turborepo-workspace";
  private static final String WEB = "apps/web/";
  private static final String PACKAGE_JSON = "package.json";

  @TempDir Path project;

  @Test
  void dryRunMarksADifferingScriptAsRequiringApproval() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    differingLint();
    final String before = fixture.read(WEB + PACKAGE_JSON);

    final NodeFixtureRun.Result dryRun = fixture.ward(initArguments("--dry-run"));

    assertThat(dryRun.exit()).as(dryRun.output()).isZero();
    assertThat(dryRun.output()).contains("replace lint script (requires approval)");
    assertThat(fixture.read(WEB + PACKAGE_JSON)).isEqualTo(before);
  }

  @Test
  void acceptingTheQuestionReplacesTheScript() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    differingLint();

    final NodeFixtureRun.Result init = fixture.wardWithInput("y\n", initArguments());

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(init.output()).contains("Replace them? [y/n]", "web lint: eslint . -> ");
    assertThat(fixture.read(WEB + PACKAGE_JSON)).contains("\"lint\": \"eslint . --format json\"");
  }

  @Test
  void decliningKeepsTheScriptAndNamesIt() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    differingLint();

    final NodeFixtureRun.Result init = fixture.wardWithInput("n\n", initArguments());

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(init.output()).contains("web keeps its lint script");
    assertThat(fixture.read(WEB + PACKAGE_JSON)).contains("\"lint\": \"eslint .\"");
  }

  @Test
  void endOfInputKeepsTheScript() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    differingLint();

    final NodeFixtureRun.Result init = fixture.wardWithInput("", initArguments());

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(init.output()).contains("web keeps its lint script");
    assertThat(fixture.read(WEB + PACKAGE_JSON)).contains("\"lint\": \"eslint .\"");
  }

  @Test
  void replaceScriptsOptionReplacesWithoutAsking() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    differingLint();

    final NodeFixtureRun.Result init =
        fixture.wardWithInput("", initArguments("--replace-scripts"));

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(init.output()).doesNotContain("Replace them?");
    assertThat(fixture.read(WEB + PACKAGE_JSON)).contains("\"lint\": \"eslint . --format json\"");
  }

  private void differingLint() throws IOException {
    Files.writeString(
        project.resolve(WEB + PACKAGE_JSON),
        "{\n  \"name\": \"web\",\n  \"private\": true,\n  \"scripts\": {\n"
            + "    \"lint\": \"eslint .\"\n  }\n}\n");
  }

  private static String[] initArguments(final String... extra) {
    final List<String> arguments =
        new ArrayList<>(
            List.of(
                "init",
                "--preset",
                "strict",
                "--integration",
                "none",
                "--ci",
                "github",
                "--agent-rules"));
    arguments.addAll(List.of(extra));
    return arguments.toArray(String[]::new);
  }
}
