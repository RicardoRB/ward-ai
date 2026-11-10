package dev.ward.application.init.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Git-ignore entries a Node project gets follow the gates init configures. */
class NodeGitignoreEntriesTest {
  private static final Project PROJECT =
      new Project(Path.of("/shop"), Language.TYPESCRIPT, BuildTool.NPM, Framework.NONE);

  private static String entries(final String... gateIds) {
    final List<InitGateOption> gates =
        List.of(gateIds).stream()
            .map(id -> new InitGateOption(id, id, List.of(id), true, "ok"))
            .toList();
    return new NodeInitSupport().gitignoreEntries(PROJECT, gates).orElseThrow();
  }

  @Test
  void reportsAreIgnoredOnlyForTheConfiguredGates() {
    assertThat(entries("tsc")).isEqualTo(".ward/reports/\n");
    assertThat(entries("vitest", "tsc")).isEqualTo(".ward/reports/\ncoverage/\n");
    assertThat(entries("jest")).isEqualTo(".ward/reports/\ncoverage/\n");
    assertThat(entries("bun-test")).isEqualTo(".ward/reports/\ncoverage/\n");
  }

  @Test
  void strykerAddsItsCacheAndHtmlReport() {
    assertThat(entries("stryker")).isEqualTo(".ward/reports/\n.stryker-tmp/\nreports/mutation/\n");
    assertThat(entries("vitest", "stryker"))
        .isEqualTo(".ward/reports/\ncoverage/\n.stryker-tmp/\nreports/mutation/\n");
  }
}
