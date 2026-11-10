package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Node init: {@code ward-format} checks formatting, and Biome's lint is replaced by Ward's rules.
 */
@DisabledOnOs(OS.WINDOWS)
class InitWardFormatNodeTest {
  @TempDir Path project;

  @Test
  void nodeInitWritesWardFormatAndNoPrettierGate() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("typescript-project", project);

    final NodeFixtureRun.Result init =
        fixture.ward("init", "--preset", "standard", "--integration", "none", "--yes");

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
  void biomeProjectGetsWardRulesAndNoBiomeGateOrConfig() throws IOException, InterruptedException {
    final NodeFixtureRun fixture = NodeFixtureRun.copy("typescript-project", project);
    final String manifest = fixture.read("package.json");
    Files.writeString(
        project.resolve("package.json"),
        manifest.replaceFirst(
            "\"devDependencies\": \\{",
            "\"devDependencies\": {\n    \"@biomejs/biome\": \"2.5.15\","));

    final NodeFixtureRun.Result init =
        fixture.ward(
            "init", "--preset", "standard", "--integration", "none", "--agent-rules", "--yes");

    assertThat(init.exit()).as(init.output()).isZero();
    assertThat(fixture.read("AGENTS.md"))
        .contains("ward-rules.toml")
        .doesNotContain("biome check --write");
    assertThat(fixture.read("ward.toml"))
        .contains("[checks.ward-format]", "[checks.ward]")
        .doesNotContain("[checks.biome]", "[checks.prettier]");
    assertThat(project.resolve("biome.jsonc")).doesNotExist();
  }
}
