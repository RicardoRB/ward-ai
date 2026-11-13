package dev.ward.application.init.python;

import dev.ward.application.init.SourceLanguageGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Python's gates on uv, Poetry or pip: types, tests, coverage, mutation testing and audit. Lint
 * comes from the Ward rules selected in {@code ward-rules.toml}. Strict adds mutation testing on
 * top of the standard gates.
 */
final class PythonGates implements SourceLanguageGates<PythonTools> {
  /** The lcov report the {@code coverage} command writes and Ward reads. */
  static final String REPORT = ".ward/reports/coverage.lcov";

  private static final List<String> STANDARD = List.of("mypy", "pytest", "coverage");
  private static final List<String> STRICT = List.of("mypy", "pytest", "coverage", "mutmut");

  @Override
  public List<InitGateOption> analysisGates(final PythonTools python) {
    final List<InitGateOption> gates = new ArrayList<>();
    if (python.framework() == Framework.DJANGO) {
      gates.add(
          python.option(
              "django-check",
              "Runs Django's system checks, failing on warnings.",
              python.command("python", "manage.py", "check", "--fail-level", "WARNING")));
    }
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
            "Writes the lcov report that Ward checks against the minimums in ward.toml.",
            python.command("coverage", "lcov", "-o", REPORT),
            coverageSettings(python)));
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
    final List<String> arguments = new ArrayList<>(List.of("run"));
    if (python.strict() && python.ownCoverageConfig()) {
      arguments.add("--branch");
    }
    arguments.addAll(List.of("-m", "pytest", "--ignore=mutants"));
    python.pytestSettings().ifPresent(settings -> arguments.add("--ds=" + settings));
    return python.command("coverage", arguments.toArray(String[]::new));
  }

  /** The line minimum, and the branch minimum when strict; Ward reads the report and decides. */
  private static Map<String, String> coverageSettings(final PythonTools python) {
    final Map<String, String> settings = new LinkedHashMap<>();
    settings.put("minimum", String.valueOf(PythonTools.coverageMinimum(python.preset())));
    if (python.strict()) {
      settings.put("minimum-branches", String.valueOf(PythonTools.STRICT_BRANCHES));
    }
    settings.put("report", REPORT);
    return settings;
  }

  /** Strict sets the mutation minimum; standard runs mutmut with no minimum. */
  private static Map<String, String> mutationSettings(final PythonTools python) {
    return python.strict() ? Map.of("minimum", PythonTools.mutationMinimum()) : Map.of();
  }
}
