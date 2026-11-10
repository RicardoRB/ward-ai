package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JavaPresetGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentRulesTemplateTest {
  private static final Project PROJECT =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private static List<InitGateOption> gates(final List<String> ids) {
    return ids.stream().map(id -> new InitGateOption(id, id, List.of(id), true, "")).toList();
  }

  @Test
  void strictPresetStatesEveryLimitAgentsMustMeet() {
    final String body =
        AgentRulesTemplate.render(
                PROJECT, gates(JavaPresetGates.STRICT), QualityPreset.STRICT, List.of())
            .body();

    assertThat(body)
        .contains(
            "Java: the `ward` check applies the Ward strict rules selected in `ward-rules.toml`",
            "branch at least 75%, line at least 85%",
            "at least 60% of mutants killed",
            "- Architecture: `ward:architecture/layers` enforces",
            "requirePluginVersions",
            "Do not add suppressions");
  }

  @Test
  void strictSwiftRulesNameTheCoverageMinimumAndWarningsAsErrors() {
    final Project swift =
        new Project(Path.of("/p"), Language.SWIFT, BuildTool.SWIFTPM, Framework.NONE);
    final List<InitGateOption> gates =
        List.of(
            new InitGateOption(
                "swift-build",
                "Builds",
                List.of("swift", "build", "-Xswiftc", "-warnings-as-errors"),
                true,
                "",
                Map.of()),
            new InitGateOption(
                "swift-coverage",
                "Coverage",
                List.of("swift", "test", "--show-codecov-path"),
                true,
                "",
                Map.of("minimum", "85")));

    final String body =
        AgentRulesTemplate.render(swift, gates, QualityPreset.STRICT, List.of()).body();

    assertThat(body).contains("must meet the 85% minimum of line coverage", "warnings are errors");
  }

  @Test
  void customSelectionListsGatesWithoutPresetLimits() {
    final String body =
        AgentRulesTemplate.render(PROJECT, gates(List.of("tests", "ward")), null, List.of()).body();

    assertThat(body).contains("`tests`, `ward`").doesNotContain("at least");
  }

  @Test
  void writesASectionIntoAgentsMd() {
    assertThat(AgentRulesTemplate.render(PROJECT, gates(List.of("tests")), null, List.of()))
        .satisfies(
            section -> {
              assertThat(section.file()).isEqualTo(Path.of("/p/AGENTS.md"));
              assertThat(section.wholeFile()).isFalse();
            });
  }
}
