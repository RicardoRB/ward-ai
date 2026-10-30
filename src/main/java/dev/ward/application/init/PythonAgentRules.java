package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The agent rule lines of a Python project's gates, part of {@link AgentRulesTemplate}. */
final class PythonAgentRules {
  private PythonAgentRules() {}

  /** Returns one line per selected Python gate, naming its command. */
  static List<String> lines(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Set<String> owned) {
    final Map<String, String> rows =
        Map.of(
            "ruff-format",
            "- Ruff formatter: `{command}` must pass; format edited files with `ruff format`.",
            "ruff",
            "- Ruff: `{command}` must report no findings. Fix the code; do not add `# noqa`"
                + " comments or `per-file-ignores`.",
            "mypy",
            "- mypy: `{command}` must report no errors. Fix the types; do not add `# type: ignore`"
                + " comments.",
            "pytest",
            "- Tests (pytest under coverage): `{command}`. New code needs tests that assert"
                + " behavior.",
            "coverage",
            "- Coverage: `{command}` must meet the "
                + PythonTools.LINE_MINIMUM
                + "% line minimum; do not lower it.",
            "pip-audit",
            "- Dependency audit (pip-audit): `{command}` in `ci` must pass; upgrade the vulnerable"
                + " package instead of ignoring the advisory.");
    return gates.stream()
        .filter(gate -> rows.containsKey(gate.id()))
        .map(gate -> rows.get(gate.id()).replace("{command}", String.join(" ", gate.command())))
        .toList();
  }
}
