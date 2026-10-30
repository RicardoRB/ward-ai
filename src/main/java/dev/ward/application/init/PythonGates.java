package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;

/** Python's gates on uv, Poetry or pip: formatting, lint, types, tests, coverage and audit. */
final class PythonGates implements SourceLanguageGates<PythonTools> {
  private static final List<String> STANDARD =
      List.of("ruff-format", "ruff", "mypy", "pytest", "coverage");

  @Override
  public List<InitGateOption> analysisGates(final PythonTools python) {
    return List.of(
        python.option(
            "ruff-format",
            "Checks formatting with ruff format.",
            python.command("ruff", "format", "--check", ".")),
        python.option(
            "ruff",
            "Lints sources with Ruff's E, F, I, B and UP rules.",
            python.command("ruff", "check", ".", "--output-format", "json")),
        python.option(
            "mypy",
            "Type-checks the project with mypy.",
            python.command("mypy", ".", "-O", "json")),
        python.option(
            "pytest",
            "Runs the tests under coverage.",
            python.command("coverage", "run", "-m", "pytest")),
        python.option("coverage", "Fails below the line coverage minimum.", coverage(python)),
        python.option(
            "pip-audit",
            "Audits the installed dependencies for known vulnerabilities (opt-in).",
            python.command("pip-audit")));
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return STANDARD;
  }

  /** Puts the threshold on the command only when the project owns its coverage config. */
  private static List<String> coverage(final PythonTools python) {
    return python.ownCoverageConfig()
        ? python.command("coverage", "report", "--fail-under=" + PythonTools.LINE_MINIMUM)
        : python.command("coverage", "report");
  }
}
