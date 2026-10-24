package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate choices that run a Node tool as a local dependency through the project's package manager.
 *
 * @param manager the project's package manager
 * @param testGate the test runner gate: {@code vitest}, {@code jest} or {@code bun-test}
 * @param available whether the package manager is on the path
 */
record NodeTools(BuildTool manager, String testGate, boolean available) {
  static final String VITEST = "vitest";
  static final String JEST = "jest";
  static final String BUN_TEST = "bun-test";

  // shortcut: every Node gate uses the standard preset's line minimum until the TypeScript strict
  // preset (#84) exists; read the selected preset then.
  static final int LINE_MINIMUM =
      (int) Math.round(QualityPreset.STANDARD.rules().jacocoMinimums().get("LINE") * 100);

  /** Resolves the tools of {@code project} from its manifest and the executables on the path. */
  static NodeTools of(
      final Project project,
      final PackageManifest manifest,
      final EnvironmentInspection environment) {
    return new NodeTools(
        project.buildTool(),
        testGate(project.buildTool(), manifest),
        environment.inspect(project.root()).hasExecutable(project.buildExecutable()));
  }

  /** A declared runner is reused; Bun has its own, and any other project gets Vitest. */
  static String testGate(final BuildTool manager, final PackageManifest manifest) {
    if (manifest.declares(VITEST)) {
      return VITEST;
    }
    if (manifest.declares(JEST)) {
      return JEST;
    }
    return manager == BuildTool.BUN ? BUN_TEST : VITEST;
  }

  /** Returns the command prefix that runs a locally installed package binary. */
  List<String> runner() {
    return runner(manager);
  }

  /** Returns the command prefix that runs a package binary installed through {@code manager}. */
  static List<String> runner(final BuildTool manager) {
    return switch (manager) {
      case PNPM -> List.of("pnpm", "exec");
      case YARN -> List.of("yarn");
      case BUN -> List.of("bunx");
      default -> List.of("npx");
    };
  }

  /** Returns {@code runner()} followed by {@code arguments}. */
  List<String> command(final String... arguments) {
    return command(manager, arguments);
  }

  /** Returns {@code manager}'s runner followed by {@code arguments}. */
  static List<String> command(final BuildTool manager, final String... arguments) {
    final List<String> command = new ArrayList<>(runner(manager));
    command.addAll(List.of(arguments));
    return List.copyOf(command);
  }

  /** The gate of the project's test runner, failing below {@link #LINE_MINIMUM}% line coverage. */
  InitGateOption tests() {
    final String description = "Runs the project's tests with coverage.";
    return switch (testGate) {
      case JEST ->
          option(
              JEST,
              description,
              command(
                  JEST,
                  "--coverage",
                  "--coverageThreshold={\"global\":{\"lines\":" + LINE_MINIMUM + "}}"));
      case BUN_TEST ->
          option(
              BUN_TEST,
              description + " (the coverage threshold is configured in bunfig.toml.)",
              List.of("bun", "test", "--coverage"));
      default ->
          option(
              VITEST,
              description,
              command(VITEST, "run", "--coverage", "--coverage.thresholds.lines=" + LINE_MINIMUM));
    };
  }

  /** A gate that runs the local tool with {@code command}. */
  InitGateOption option(final String id, final String description, final List<String> command) {
    final String executable = manager.displayName();
    return new InitGateOption(
        id,
        description,
        command,
        available,
        available
            ? executable + " command available; init adds the tool as a dev dependency."
            : executable + " is unavailable; install " + executable + " before running this gate.");
  }
}
