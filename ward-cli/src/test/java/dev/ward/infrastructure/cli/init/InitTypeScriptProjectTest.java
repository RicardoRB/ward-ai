package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** {@code ward init} on the offline TypeScript fixture, with stub {@code npm} and {@code npx}. */
@DisabledOnOs(OS.WINDOWS)
class InitTypeScriptProjectTest {
  @TempDir Path project;

  private NodeFixtureRun fixture;

  @BeforeEach
  void copyFixture() throws IOException {
    fixture = NodeFixtureRun.copy("typescript-project", project);
  }

  @Test
  void standardPresetConfiguresTheProjectAndItsPrePushProfilePasses()
      throws IOException, InterruptedException {
    final NodeFixtureRun.Result init =
        fixture.ward(
            "init",
            "--preset",
            "standard",
            "--integration",
            "none",
            "--ci",
            "github",
            "--agent-rules",
            "--yes");

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read("ward.toml"))
        .contains(
            "[checks.tsc]",
            "[checks.vitest]",
            "[checks.ward-format]",
            "[checks.ward]",
            "command = \"npx vitest run --coverage\"")
        .doesNotContain("[checks.eslint]", "[checks.biome]", "[checks.semgrep]")
        .doesNotContain("checkstyle", "mvnw");
    assertThat(fixture.read("package.json"))
        .contains("\"typescript\": \"^5.9.3\",", "\"@vitest/coverage-v8\": \"4.0.18\"")
        .doesNotContain("eslint", "biome");
    assertThat(fixture.read("package-lock.json")).contains("\"stub\": \"installed\"");
    assertThat(fixture.read("ward-rules.toml"))
        .contains("preset = \"standard\"", "languages = [\"typescript\", \"swift\"]");
    assertThat(project.resolve("eslint.config.mjs")).doesNotExist();
    assertThat(fixture.read("vitest.config.mjs"))
        .startsWith("// managed-by: ward\n")
        .contains("reporter: [\"text\", \"lcov\"]")
        .doesNotContain("threshold");
    assertThat(fixture.read("ward.toml"))
        .contains("[checks.coverage]", "uses = \"coverage\"", "minimum = 70")
        .doesNotContain("minimum-branches");
    assertThat(fixture.read(".github/workflows/ward.yml"))
        .contains("actions/setup-node@v5", "node-version: \"24\"", "run: npm ci")
        .doesNotContain("setup-java");
    assertThat(fixture.read(".gitignore")).startsWith("node_modules/\n").contains(".ward/reports/");
    assertThat(fixture.read("AGENTS.md"))
        .contains("ward-rules.toml", "Tests (Vitest)")
        .doesNotContain("ESLint", "JaCoCo");
    assertThat(project.resolve("config")).doesNotExist();

    final NodeFixtureRun.Result check = fixture.ward("check", "--profile", "pre-push");

    assertThat(check.exit()).as(check.output()).isZero();
  }
}
