package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate choices that run a Python tool through the project's package manager, in the environment
 * that holds the {@code ward} dependency group.
 *
 * @param manager the project's package manager: uv, Poetry or pip
 * @param available whether the manager's executable is on the path
 * @param preset the selected preset, or {@code null} for a custom selection
 * @param ownCoverageConfig whether the project keeps its own coverage config, which init leaves
 *     unchanged, so the coverage threshold goes on the command instead
 */
record PythonTools(
    BuildTool manager, boolean available, QualityPreset preset, boolean ownCoverageConfig) {
  /** The line coverage minimum of the standard preset, in percent. */
  static final int LINE_MINIMUM = 80;

  static final String GROUP = "ward";
  private static final String VENV_BIN = ".venv/bin/";

  /** Resolves the tools of {@code project} from the executables on the path and its configs. */
  static PythonTools of(
      final Project project, final InitInputs inputs, final QualityPreset preset) {
    final BuildTool manager = project.buildTool();
    final String executable = manager == BuildTool.PIP ? "python3" : project.buildExecutable();
    return new PythonTools(
        manager,
        inputs.environment().inspect(project.root()).hasExecutable(executable),
        preset,
        inputs.pythonConfigs().userConfig(project.root(), "coverage").isPresent());
  }

  /** Returns the command that runs {@code tool} with {@code arguments} in this project. */
  List<String> command(final String tool, final String... arguments) {
    return command(manager, tool, arguments);
  }

  /** Returns the command that runs {@code tool} with {@code arguments} under {@code manager}. */
  static List<String> command(
      final BuildTool manager, final String tool, final String... arguments) {
    final List<String> command = new ArrayList<>(runner(manager, tool));
    command.addAll(List.of(arguments));
    return List.copyOf(command);
  }

  private static List<String> runner(final BuildTool manager, final String tool) {
    return switch (manager) {
      case POETRY -> List.of("poetry", "run", tool);
      case PIP -> List.of(VENV_BIN + tool);
      default -> List.of("uv", "run", "--group", GROUP, tool);
    };
  }

  /** Returns a gate choice for {@code id}, with availability resolved against the manager. */
  InitGateOption option(final String id, final String description, final List<String> command) {
    final String name = manager.displayName();
    return new InitGateOption(
        id,
        description,
        command,
        available,
        available
            ? name + " command available; init adds the tool to the " + GROUP + " group."
            : name + " is unavailable; install " + name + " before running this gate.");
  }
}
