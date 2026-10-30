package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code ward init} on a Turborepo workspace: members, turbo.json and the root's turbo dependency.
 */
class TurborepoInitTest {
  private static final String PACKAGE_JSON = "package.json";
  private static final String TURBO_JSON = "turbo.json";
  private static final String FIXTURE = "turborepo-workspace";
  private static final String WEB = "apps/web/";

  @TempDir Path project;

  @Test
  void turborepoWorkspaceGetsMemberScriptsTurboTasksAndAPinnedRootTurbo()
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    final String configOnly = fixture.read("packages/eslint-config/package.json");

    final NodeFixtureRun.Result init = init(fixture);

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read(PACKAGE_JSON)).contains("\"turbo\": \"2.11.9\"");
    assertThat(fixture.read(WEB + PACKAGE_JSON))
        .contains("\"check-types\": \"tsc --noEmit --pretty false\"");
    assertThat(fixture.read("packages/ui/" + PACKAGE_JSON))
        .contains("\"check-types\": \"tsc --noEmit --pretty false\"");
    assertThat(fixture.read("packages/eslint-config/" + PACKAGE_JSON)).isEqualTo(configOnly);
    assertThat(fixture.read(TURBO_JSON))
        .contains(
            "\"check-types\": {}", "\"lint\": {}", "\"mutation\": {\"outputs\": [\"reports/**\"]}")
        .contains("\"test\": {\"dependsOn\": [\"^build\"], \"outputs\": [\"coverage/**\"]}");
    assertThat(fixture.read("apps/web/eslint.config.mjs")).startsWith("// managed-by: ward");
    assertThat(fixture.read("packages/ui/" + PACKAGE_JSON)).contains("\"vitest\":");
    assertThat(fixture.read("ward.toml")).contains("turbo", "run", "--filter=...");
  }

  @Test
  void memberOwnEslintConfigIsKept() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    final String own = "import base from \"@repo/eslint-config\";\nexport default [base];\n";
    Files.writeString(project.resolve("apps/web/eslint.config.mjs"), own);

    final NodeFixtureRun.Result init = init(fixture);

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read("apps/web/eslint.base.mjs")).isEqualTo(own);
    assertThat(fixture.read("apps/web/eslint.config.mjs")).contains("from \"./eslint.base.mjs\"");
  }

  @Test
  void failurePartwayRollsEveryMemberAndTheRootBack() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(FIXTURE, project);
    final String root = fixture.read(PACKAGE_JSON);
    final String web = fixture.read(WEB + PACKAGE_JSON);
    final String turbo = fixture.read(TURBO_JSON);
    Files.writeString(project.resolve("packages/ui/" + PACKAGE_JSON), "{\"name\":\"@repo/ui\"}");
    final String ui = fixture.read("packages/ui/" + PACKAGE_JSON);

    final NodeFixtureRun.Result init = init(fixture);

    assertThat(init.exit()).isNotZero();
    assertThat(fixture.read(PACKAGE_JSON)).isEqualTo(root);
    assertThat(fixture.read(WEB + PACKAGE_JSON)).isEqualTo(web);
    assertThat(fixture.read("packages/ui/" + PACKAGE_JSON)).isEqualTo(ui);
    assertThat(fixture.read(TURBO_JSON)).isEqualTo(turbo);
    assertThat(project.resolve("ward.toml")).doesNotExist();
    assertThat(project.resolve("apps/web/eslint.config.mjs")).doesNotExist();
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
