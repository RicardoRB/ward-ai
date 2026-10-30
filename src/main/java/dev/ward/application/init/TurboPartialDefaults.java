package dev.ward.application.init;

import dev.ward.application.init.PartialCheckDefaults.Table;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The partial-check table of a Turborepo root's lint gate. */
final class TurboPartialDefaults {
  private static final Set<String> LINT_GATES = Set.of("eslint", "biome");
  private static final List<String> SOURCES =
      List.of(
          "**/*.ts",
          "**/*.tsx",
          "**/*.mts",
          "**/*.cts",
          "**/*.js",
          "**/*.jsx",
          "**/*.mjs",
          "**/*.cjs");

  private TurboPartialDefaults() {}

  /**
   * Returns the lint gate's table for a Turborepo root: an edit narrows {@code turbo run lint} to
   * the package that holds it and its dependents. Root configs and {@code turbo.json} run every
   * package. Empty for any other gate or project.
   */
  static Optional<Table> forGate(final String gate, final Project project) {
    final Optional<TaskRunnerStrategy> strategy = TaskRunnerStrategies.of(project);
    if (!project.taskRunner().isRootRunner() || strategy.isEmpty() || !LINT_GATES.contains(gate)) {
      return Optional.empty();
    }
    return Optional.of(
        new Table(
            SOURCES,
            List.of(
                "turbo.json",
                "package.json",
                "eslint.config.*",
                ".eslintrc*",
                "biome.json",
                "biome.jsonc",
                ".biome.json",
                ".biome.jsonc"),
            List.of("{paths}"),
            strategy.get().packageFilter(),
            null,
            strategy.get().rootTaskCommand(project.buildTool(), TurborepoPlan.LINT)));
  }
}
