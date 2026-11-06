package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** {@code --format-engine ward} on Node: Prettier is replaced, and Biome keeps linting only. */
@DisabledOnOs(OS.WINDOWS)
class InitFormatEngineNodeTest {
  @TempDir Path project;

  @Test
  void prettierIsReplacedByWardFormatAndTheSourcesGetTwoSpaceIndents()
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("typescript-project", project);

    final NodeFixtureRun.Result init =
        fixture.ward(
            "init",
            "--preset",
            "standard",
            "--integration",
            "none",
            "--format-engine",
            "ward",
            "--yes");

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read("ward.toml"))
        .contains("[checks.ward-format]", "uses = \"ward-format\"")
        .doesNotContain("[checks.prettier]");
    assertThat(fixture.read("package.json")).doesNotContain("\"prettier\": \"3.9.10\"");
    assertThat(fixture.read(".editorconfig"))
        .contains("[*.{ts,tsx,js,jsx,mjs,cjs}]\nindent_size = 2\n")
        .doesNotContain("ward_continuation_indent_size");
  }

  @Test
  void biomeKeepsLintingWithItsFormatterOffAndWardFormatChecksFormatting()
      throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("typescript-project", project);
    final String manifest = fixture.read("package.json");
    Files.writeString(
        project.resolve("package.json"),
        manifest.replaceFirst(
            "\"devDependencies\": \\{",
            "\"devDependencies\": {\n    \"@biomejs/biome\": \"2.5.15\","));

    final NodeFixtureRun.Result init =
        fixture.ward(
            "init",
            "--preset",
            "standard",
            "--integration",
            "none",
            "--format-engine",
            "ward",
            "--yes");

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read("ward.toml"))
        .contains("[checks.biome]", "[checks.ward-format]")
        .doesNotContain("[checks.prettier]");
    assertThat(fixture.read("biome.jsonc")).contains("\"formatter\": { \"enabled\": false }");
    assertThat(fixture.read("ward.toml")).doesNotContain("prettier");
  }
}
