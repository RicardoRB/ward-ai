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
    differingCheckTypes();
    final String before = fixture.read(WEB + PACKAGE_JSON);

    final NodeFixtureRun.Result dryRun = fixture.ward(initArguments("--dry-run"));

    assertThat(dryRun.exit()).as(dryRun.output()).isZero();
    assertThat(dryRun.output()).contains("replace check-types script (requires approval)");
    assertThat(fixture.read(WEB + PACKAGE_JSON)).isEqualTo(before);
  }

  @Test
  void acceptingTheQuestionReplacesTheScript() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    differingCheckTypes();

    final NodeFixtureRun.Result init = fixture.wardWithInput("y\n", initArguments());

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(init.output()).contains("Replace them? [y/n]", "web check-types: tsc -p . -> ");
    assertThat(fixture.read(WEB + PACKAGE_JSON))
        .contains("\"check-types\": \"tsc --noEmit --pretty false\"");
  }

  @Test
  void decliningKeepsTheScriptAndNamesIt() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    differingCheckTypes();

    final NodeFixtureRun.Result init = fixture.wardWithInput("n\n", initArguments());

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(init.output()).contains("web keeps its check-types script");
    assertThat(fixture.read(WEB + PACKAGE_JSON)).contains("\"check-types\": \"tsc -p .\"");
  }

  @Test
  void endOfInputKeepsTheScript() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    differingCheckTypes();

    final NodeFixtureRun.Result init = fixture.wardWithInput("", initArguments());

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(init.output()).contains("web keeps its check-types script");
    assertThat(fixture.read(WEB + PACKAGE_JSON)).contains("\"check-types\": \"tsc -p .\"");
  }

  @Test
  void replaceScriptsOptionReplacesWithoutAsking() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    differingCheckTypes();

    final NodeFixtureRun.Result init =
        fixture.wardWithInput("", initArguments("--replace-scripts"));

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(init.output()).doesNotContain("Replace them?");
    assertThat(fixture.read(WEB + PACKAGE_JSON))
        .contains("\"check-types\": \"tsc --noEmit --pretty false\"");
  }

  private void differingCheckTypes() throws IOException {
    Files.writeString(
        project.resolve(WEB + PACKAGE_JSON),
        "{\n  \"name\": \"web\",\n  \"private\": true,\n  \"scripts\": {\n"
            + "    \"check-types\": \"tsc -p .\"\n  }\n}\n");
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
