package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Python's gates on uv, Poetry or pip: formatting, lint, types, tests, coverage, mutation testing
 * and audit. Strict adds mutation testing and the stricter rules on top of the standard gates.
 */
final class PythonGates implements SourceLanguageGates<PythonTools> {
  private static final List<String> STANDARD =
      List.of("ruff-format", "ruff", "mypy", "pytest", "coverage");
  private static final List<String> STRICT =
      List.of("ruff-format", "ruff", "mypy", "pytest", "coverage", "import-linter", "mutmut");

  @Override
  public List<InitGateOption> analysisGates(final PythonTools python) {
    final List<InitGateOption> gates = new ArrayList<>();
    gates.add(
        python.option(
            "ruff-format",
            "Checks formatting with ruff format.",
            python.command("ruff", "format", "--check", ".")));
    gates.add(
        python.option(
            "ruff",
            "Lints sources with Ruff's rules: E, F, I, B and UP, plus W, C4, SIM, RUF, PTH and C90 when strict.",
            ruff(python)));
    gates.add(
        python.option(
            "mypy",
            "Type-checks the project with mypy, in strict mode when strict.",
            mypy(python)));
    gates.add(
        python.option(
            "pytest",
            "Runs the tests under coverage, measuring branches when strict.",
            pytest(python)));
    gates.add(
        python.option(
            "coverage",
            "Fails below the line coverage minimum, the total of lines and branches when strict.",
            coverage(python)));
    gates.add(
        python.option(
            "pip-audit",
            "Audits the installed dependencies for known vulnerabilities (opt-in).",
            python.command("pip-audit")));
    gates.add(
        python.option(
            "import-linter",
            "Checks the architecture style's import contracts with import-linter.",
            python.command("lint-imports")));
    gates.add(
        python.option(
            "mutmut",
            "Runs mutation testing with mutmut; strict fails below a 60% mutation score.",
            python.command("mutmut", "run"),
            mutationSettings(python)));
    return List.copyOf(gates);
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? STRICT : STANDARD;
  }

  /**
   * Ward's Ruff config carries the strict rules, so the command adds them only for a user config.
   */
  private static List<String> ruff(final PythonTools python) {
    final List<String> command = new ArrayList<>(python.command("ruff", "check", "."));
    if (python.strict() && python.ownRuffConfig()) {
      command.addAll(PythonTools.ruffStrictFlags());
    }
    command.addAll(List.of("--output-format", "json"));
    return List.copyOf(command);
  }

  private static List<String> mypy(final PythonTools python) {
    final List<String> command = new ArrayList<>(python.command("mypy", "."));
    if (python.strict() && python.ownMypyConfig()) {
      command.add("--strict");
    }
    command.addAll(List.of("-O", "json"));
    return List.copyOf(command);
  }

  /**
   * Strict measures branches, which Ward's own coverage config does and a user config may not. The
   * tests skip {@code mutants/}, which mutmut fills with copies of them.
   */
  private static List<String> pytest(final PythonTools python) {
    return python.strict() && python.ownCoverageConfig()
        ? python.command("coverage", "run", "--branch", "-m", "pytest", "--ignore=mutants")
        : python.command("coverage", "run", "-m", "pytest", "--ignore=mutants");
  }

  /** Puts the threshold on the command only when the project owns its coverage config. */
  private static List<String> coverage(final PythonTools python) {
    return python.ownCoverageConfig()
        ? python.command(
            "coverage", "report", "--fail-under=" + PythonTools.coverageMinimum(python.preset()))
        : python.command("coverage", "report");
  }

  /** Strict sets the mutation minimum; standard runs mutmut with no minimum. */
  private static Map<String, String> mutationSettings(final PythonTools python) {
    return python.strict() ? Map.of("minimum", PythonTools.mutationMinimum()) : Map.of();
  }
}
