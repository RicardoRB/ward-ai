package dev.ward.application.init.python;

import dev.ward.application.init.CiWorkflowTemplate;
import dev.ward.domain.project.Project;

/** The CI workflow steps that set up Python and install the {@code ward} group. */
final class PythonCiSetup {
  private static final String GROUP = PythonTools.GROUP;

  private PythonCiSetup() {}

  /** Returns the setup and install steps for {@code project}'s package manager. */
  static String steps(final Project project) {
    final String python =
        CiWorkflowTemplate.STEP
            + "Set up Python\n"
            + "        uses: actions/setup-python@v6\n"
            + "        with:\n"
            + "          python-version: \""
            + project.languageVersion()
            + "\"\n";
    return switch (project.buildTool()) {
      case UV ->
          python
              + CiWorkflowTemplate.STEP
              + "Set up uv\n"
              + "        uses: astral-sh/setup-uv@v7\n"
              + install("uv sync --locked --group " + GROUP);
      case POETRY ->
          python
              + CiWorkflowTemplate.STEP
              + "Install Poetry\n"
              + "        run: pipx install poetry\n"
              + install("poetry check --lock && poetry install --with " + GROUP);
      default -> python + install(pipInstall());
    };
  }

  /** pip installs the group, or the requirements file of a project without pyproject.toml. */
  private static String pipInstall() {
    return "python -m venv .venv && if [ -f requirements-ward.txt ]; then"
        + " .venv/bin/python -m pip install -r requirements-ward.txt; else"
        + " .venv/bin/python -m pip install --group "
        + GROUP
        + "; fi";
  }

  private static String install(final String command) {
    return CiWorkflowTemplate.STEP + "Install dependencies\n" + "        run: " + command + "\n";
  }
}
