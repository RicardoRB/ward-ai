package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** {@code detect} and the JSON report show the Node version of TypeScript projects only. */
@DisabledOnOs(OS.WINDOWS)
class WardCliNodeProjectTest extends WardCliIntegrationSupport {
  private static final String LANGUAGE_VERSION = "languageVersion";
  private static final String PROJECT = "project";

  @TempDir Path node;

  @Test
  void detectPrintsTheNodeVersionOfATypeScriptProject() throws IOException {
    typeScriptProject();

    assertThat(run("detect", "-C", node.toString())).isZero();
    assertThat(out.toString()).contains("TypeScript", "pnpm", "Node:         22");
  }

  @Test
  void detectPrintsNoNodeLineForJava() {
    assertThat(run("detect", "-C", project.toString())).isZero();
    assertThat(out.toString()).doesNotContain("Node:");
  }

  @Test
  void jsonReportHasTheLanguageVersionOfANodeProject() throws IOException {
    typeScriptProject();
    Files.writeString(
        node.resolve(WARD_TOML),
        """
        version = 2
        [checks.format]
        command = ["true"]
        """);

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", node.toString())).isZero();
    assertThat(JsonTree.parse(out.toString()).get(PROJECT).get(LANGUAGE_VERSION).asString())
        .isEqualTo("22");
  }

  @Test
  void jsonReportOfAMavenProjectHasNoLanguageVersion() {
    assertThat(run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "format", "-C", project.toString()))
        .isZero();
    out.getBuffer().setLength(0);

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString())).isZero();
    assertThat(JsonTree.parse(out.toString()).get(PROJECT).has(LANGUAGE_VERSION)).isFalse();
  }

  private void typeScriptProject() throws IOException {
    Files.writeString(
        node.resolve("package.json"),
        "{\"packageManager\": \"pnpm@9.12.0\", \"engines\": {\"node\": \">=22\"}}");
    Files.writeString(node.resolve("tsconfig.json"), "{}");
  }
}
