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
  @CsvSource({
    "javascript-commonjs-project, eslint.config.mjs, true",
    "javascript-esm-project, eslint.config.js, false"
  })
  void standardPresetConfiguresTheProjectAndItsPrePushProfilePasses(
      final String name, final String eslintConfig, final boolean commonJs)
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
            "[checks.prettier]",
            "[checks.eslint]",
            "command = \"npx vitest run --coverage\"")
        .doesNotContain("[checks.tsc]", "checkstyle", "mvnw");
    assertThat(fixture.read("package.json"))
        .contains(
            "\"eslint\": \"10.12.0\"",
            "\"@eslint/js\": \"10.0.1\"",
            "\"globals\": \"17.13.0\"",
            "\"prettier\": \"3.9.10\"",
            "\"vitest\": \"4.0.18\"")
        .doesNotContain("typescript");
    assertThat(fixture.read(eslintConfig))
        .startsWith("// managed-by: ward preset=standard\n")
        .contains("globals.node");
    assertThat(fixture.read(eslintConfig).contains("sourceType: \"commonjs\"")).isEqualTo(commonJs);
    assertThat(fixture.read("vitest.config.mjs"))
        .startsWith("// managed-by: ward preset=standard\n")
        .contains("lines: 70,")
        .doesNotContain("branches");
    assertThat(fixture.read(".github/workflows/ward.yml"))
        .contains("actions/setup-node@v5", "run: npm ci")
        .doesNotContain("setup-java");
    assertThat(fixture.read(".gitignore")).startsWith("node_modules/\n").contains(".ward/reports/");
    assertThat(fixture.read("AGENTS.md"))
        .contains("ESLint's recommended JavaScript rules", "Tests (Vitest)")
        .doesNotContain("tsc", "typescript-eslint", "JaCoCo");
    assertThat(project.resolve("config")).doesNotExist();

    final NodeFixtureRun.Result check = fixture.ward("check", "--profile", "pre-push");

    assertThat(check.exit()).as(check.output()).isZero();
  }
}
