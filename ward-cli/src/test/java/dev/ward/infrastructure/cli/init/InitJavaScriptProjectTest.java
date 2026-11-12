package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@code ward init} on the offline CommonJS and ESM JavaScript fixtures, with stub npm and npx. */
@DisabledOnOs(OS.WINDOWS)
class InitJavaScriptProjectTest {
  @TempDir Path project;

  @ParameterizedTest
  @CsvSource({"javascript-commonjs-project", "javascript-esm-project"})
  void standardPresetConfiguresTheProjectAndItsPrePushProfilePasses(final String name)
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy(name, project);

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
            "[checks.vitest]",
            "[checks.ward]",
            "[checks.ward-format]",
            "command = \"npx vitest run --coverage\"")
        .doesNotContain("[checks.eslint]", "[checks.tsc]", "checkstyle", "mvnw");
    assertThat(fixture.read("ward-rules.toml"))
        .contains("preset = \"standard\"", "languages = [\"javascript\", \"swift\"]");
    assertThat(fixture.read("package.json"))
        .contains("\"vitest\": \"4.0.18\"")
        .doesNotContain("eslint", "globals", "typescript");
    assertThat(project.resolve("eslint.config.mjs")).doesNotExist();
    assertThat(project.resolve("eslint.config.js")).doesNotExist();
    assertThat(fixture.read("vitest.config.mjs"))
        .startsWith("// managed-by: ward\n")
        .contains("reporter: [\"text\", \"lcov\"]")
        .doesNotContain("threshold");
    assertThat(fixture.read("ward.toml"))
        .contains("[checks.coverage]", "uses = \"coverage\"", "minimum = 70")
        .doesNotContain("minimum-branches");
    assertThat(fixture.read(".github/workflows/ward.yml"))
        .contains("actions/setup-node@v5", "run: npm ci")
        .doesNotContain("setup-java");
    assertThat(fixture.read(".gitignore")).startsWith("node_modules/\n").contains(".ward/reports/");
    assertThat(fixture.read("AGENTS.md"))
        .contains("ward-rules.toml", "Tests (Vitest)")
        .doesNotContain("tsc", "ESLint", "JaCoCo");
    assertThat(project.resolve("config")).doesNotExist();

    final NodeFixtureRun.Result check = fixture.ward("check", "--profile", "pre-push");

    assertThat(check.exit()).as(check.output()).isZero();
  }
}
