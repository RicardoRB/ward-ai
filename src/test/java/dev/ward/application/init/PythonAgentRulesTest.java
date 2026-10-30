package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The agent rules a Python project gets name the strict limits and the mutation minimum. */
class PythonAgentRulesTest {
  private static final Project PROJECT =
      new Project(Path.of("/shop"), Language.PYTHON, BuildTool.UV, Framework.NONE);

  @Test
  void strictRulesNameTheBranchCoverageLimitAndTheMutationMinimum() {
    final List<String> lines =
        PythonAgentRules.lines(
            PROJECT, gates("coverage", "mutmut"), QualityPreset.STRICT, Set.of());

    assertThat(String.join("\n", lines))
        .contains("85% minimum of lines and branches")
        .contains("mutmut", "at least 60%");
  }

  @Test
  void standardRulesNameTheLineLimitOnly() {
    final List<String> lines =
        PythonAgentRules.lines(PROJECT, gates("coverage"), QualityPreset.STANDARD, Set.of());

    assertThat(String.join("\n", lines)).contains("80% minimum of lines;");
  }

  @Test
  void rulesForbidSuppressions() {
    final List<String> lines =
        PythonAgentRules.lines(PROJECT, gates("ruff", "mypy"), QualityPreset.STANDARD, Set.of());

    assertThat(String.join("\n", lines))
        .contains("`# noqa`", "`# type: ignore`", "per-file-ignores");
  }

  private static List<InitGateOption> gates(final String... ids) {
    return java.util.Arrays.stream(ids)
        .map(id -> new InitGateOption(id, List.of(id), true, ""))
        .toList();
  }
}
