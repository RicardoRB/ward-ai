package dev.ward.application.init.python;

import dev.ward.application.init.AgentRulesTemplate;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The agent rule lines of a Python project's gates, part of {@link AgentRulesTemplate}. */
final class PythonAgentRules {
  private static final String FASTAPI_OPENAPI =
      "- FastAPI: after a route change, re-export openapi.json and commit it; the openapi gates"
          + " compare it with the baseline.";
  private static final String FLASK_FACTORY =
      "- Flask: the app is built by `create_app`; keep it importable, since the pytest-flask"
          + " fixtures load it.";

  private PythonAgentRules() {}

  /** Returns one line per selected Python gate, naming its command. */
  static List<String> lines(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Set<String> owned) {
    final Map<String, String> rows =
        Map.of(
            "django-check",
            "- Django: `{command}` must report no issues; fix the settings or model that fails.",
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
                + PythonTools.coverageMinimum(preset)
                + "% minimum"
                + (preset == QualityPreset.STRICT ? " of lines and branches" : " of lines")
                + "; do not lower it.",
            "import-linter",
            "- Import rules (import-linter): `{command}` must pass. Fix the import that breaks a"
                + " contract; do not edit or weaken the `.importlinter` contracts.",
            "mutmut",
            "- Mutation testing (mutmut): `{command}` in `ci` must score at least 60%; kill the"
                + " surviving mutants with tests, do not weaken the code under test.",
            "pip-audit",
            "- Dependency audit (pip-audit): `{command}` in `ci` must pass; upgrade the vulnerable"
                + " package instead of ignoring the advisory.");
    final List<String> gateLines =
        gates.stream()
            .filter(gate -> rows.containsKey(gate.id()))
            .map(gate -> rows.get(gate.id()).replace("{command}", String.join(" ", gate.command())))
            .toList();
    return java.util.stream.Stream.concat(
            gateLines.stream(), frameworkLines(project.framework(), gates).stream())
        .toList();
  }

  /**
   * Returns the framework's own rule: FastAPI's openapi export when an openapi gate is selected.
   */
  private static List<String> frameworkLines(
      final Framework framework, final List<InitGateOption> gates) {
    return switch (framework) {
      case FASTAPI ->
          gates.stream().anyMatch(gate -> gate.id().startsWith("openapi"))
              ? List.of(FASTAPI_OPENAPI)
              : List.of();
      case FLASK -> List.of(FLASK_FACTORY);
      default -> List.of();
    };
  }
}
