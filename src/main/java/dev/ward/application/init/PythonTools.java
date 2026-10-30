package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.PythonToolConfigs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gate choices that run a Python tool through the project's package manager, in the environment
 * that holds the {@code ward} dependency group.
 *
 * @param manager the project's package manager: uv, Poetry or pip
 * @param available whether the manager's executable is on the path
 * @param preset the selected preset, or {@code null} for a custom selection
 * @param ownRuffConfig whether the project keeps its own Ruff config; init then passes the strict
 *     rules on the command instead of writing them
 * @param ownMypyConfig whether the project keeps its own mypy config
 * @param ownCoverageConfig whether the project keeps its own coverage config
 */
record PythonTools(
    BuildTool manager,
    boolean available,
    QualityPreset preset,
    boolean ownRuffConfig,
    boolean ownMypyConfig,
    boolean ownCoverageConfig) {
  static final String GROUP = "ward";
  private static final String VENV_BIN = ".venv/bin/";
  private static final int STANDARD_COVERAGE = 80;
  private static final int STRICT_COVERAGE = 85;
  private static final int STRICT_MUTATION = 60;
  private static final String STRICT_RUFF_RULES = "W,C4,SIM,RUF,PTH,C90";
  private static final String STRICT_COMPLEXITY = "lint.mccabe.max-complexity=10";

  /** Resolves the tools of {@code project} from the executables on the path and its configs. */
  static PythonTools of(
      final Project project,
      final PythonToolConfigs configs,
      final boolean executableAvailable,
      final QualityPreset preset) {
    return new PythonTools(
        project.buildTool(),
        executableAvailable,
        preset,
        configs.userConfig(project.root(), "ruff").isPresent(),
        configs.userConfig(project.root(), "mypy").isPresent(),
        configs.userConfig(project.root(), "coverage").isPresent());
  }

  /** Returns the executable whose presence means the manager is available. */
  static String executable(final Project project) {
    return project.buildTool() == BuildTool.PIP ? "python3" : project.buildExecutable();
  }

  /** Returns the line coverage minimum of {@code preset}, in percent; strict adds branches. */
  static int coverageMinimum(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? STRICT_COVERAGE : STANDARD_COVERAGE;
  }

  /** Returns whether {@code preset} is strict. */
  boolean strict() {
    return preset == QualityPreset.STRICT;
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
    return option(id, description, command, Map.of());
  }

  /** Returns a gate choice for {@code id} with {@code settings}, availability as above. */
  InitGateOption option(
      final String id,
      final String description,
      final List<String> command,
      final Map<String, String> settings) {
    final String name = manager.displayName();
    return new InitGateOption(
        id,
        description,
        command,
        available,
        available
            ? name + " command available; init adds the tool to the " + GROUP + " group."
            : name + " is unavailable; install " + name + " before running this gate.",
        settings);
  }

  /** Returns the mutation score minimum that strict sets on the mutmut gate. */
  static String mutationMinimum() {
    return String.valueOf(STRICT_MUTATION);
  }

  /** Returns the Ruff rule sets strict adds on a project's own Ruff config. */
  static List<String> ruffStrictFlags() {
    return List.of("--extend-select", STRICT_RUFF_RULES, "--config", STRICT_COMPLEXITY);
  }
}
