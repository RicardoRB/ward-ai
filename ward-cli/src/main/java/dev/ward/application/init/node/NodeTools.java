package dev.ward.application.init.node;

import dev.ward.application.init.InitInputs;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.node.PackageManifest;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate choices that run a Node tool as a local dependency through the project's package manager.
 *
 * @param manager the project's package manager
 * @param testGate the test runner gate: {@code vitest}, {@code jest} or {@code bun-test}
 * @param available whether the package manager is on the path
 * @param preset the selected preset, or {@code null} for a custom selection
 * @param ownTestConfig whether the project keeps its own test runner config, which init leaves
 *     unchanged
 */
public record NodeTools(
    BuildTool manager,
    String testGate,
    boolean available,
    QualityPreset preset,
    boolean ownTestConfig) {
  public static final String VITEST = "vitest";
  public static final String JEST = "jest";
  public static final String BUN_TEST = "bun-test";
  public static final String NG_TEST = "ng-test";
  static final String LINE = "LINE";
  static final String BRANCH = "BRANCH";
  private static final int PERCENT = 100;

  /**
   * Resolves the tools of {@code project} from its manifest, the executables on the path and the
   * configs it keeps, for {@code preset}.
   */
  public static NodeTools of(
      final Project project,
      final PackageManifest manifest,
      final InitInputs inputs,
      final QualityPreset preset) {
    final String testGate = testGate(project, manifest);
    return new NodeTools(
        project.buildTool(),
        testGate,
        inputs.environment().inspect(project.root()).hasExecutable(project.buildExecutable()),
        preset,
        inputs.configs().userConfig(project.root(), testGate).isPresent());
  }

  /** Returns these tools with {@code gate} as the test runner gate. */
  NodeTools withTestGate(final String gate) {
    return new NodeTools(manager, gate, available, preset, ownTestConfig);
  }

  /**
   * Returns {@code preset}'s minimum percentage for coverage {@code counter}, such as {@code LINE},
   * or {@code null} when it sets none; a custom selection uses the standard preset's minimums.
   */
  static Integer minimum(final QualityPreset preset, final String counter) {
    final Double ratio =
        (preset == null ? QualityPreset.STANDARD : preset).rules().jacocoMinimums().get(counter);
    return ratio == null ? null : (int) Math.round(ratio * PERCENT);
  }

  /**
   * A declared runner is reused. Angular gets {@code ng test} unless it declares Jest, since the
   * Vitest that its scaffold adds is not a choice. Bun has its own, and any other project gets
   * Vitest.
   */
  public static String testGate(final Project project, final PackageManifest manifest) {
    if (project.framework() == Framework.ANGULAR) {
      return manifest.declares(JEST) ? JEST : NG_TEST;
    }
    if (manifest.declares(VITEST)) {
      return VITEST;
    }
    if (manifest.declares(JEST)) {
      return JEST;
    }
    return project.buildTool() == BuildTool.BUN ? BUN_TEST : VITEST;
  }

  /** Returns the command prefix that runs a locally installed package binary. */
  public List<String> runner() {
    return runner(manager);
  }

  /** Returns the command prefix that runs a package binary installed through {@code manager}. */
  public static List<String> runner(final BuildTool manager) {
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
  public static List<String> command(final BuildTool manager, final String... arguments) {
    final List<String> command = new ArrayList<>(runner(manager));
    command.addAll(List.of(arguments));
    return List.copyOf(command);
  }

  /**
   * The gate of the project's test runner, failing below the preset's coverage minimums. A config
   * Ward writes holds them; a project's own config gets them as command-line flags.
   */
  InitGateOption tests() {
    final String description = "Runs the project's tests with coverage.";
    final Integer branches = minimum(preset, BRANCH);
    return switch (testGate) {
      case JEST -> {
        final String threshold =
            "--coverageThreshold={\"global\":{\"lines\":"
                + minimum(preset, LINE)
                + (branches == null ? "" : ",\"branches\":" + branches)
                + "}}";
        yield option(
            JEST,
            description,
            ownTestConfig ? command(JEST, "--coverage", threshold) : command(JEST, "--coverage"));
      }
      case BUN_TEST ->
          option(
              BUN_TEST,
              description + " (the coverage threshold is configured in bunfig.toml.)",
              List.of("bun", "test", "--coverage"));
      case NG_TEST ->
          option(
              NG_TEST,
              description + " (the project's angular.json sets any coverage threshold.)",
              command("ng", "test", "--watch=false", "--coverage"));
      default -> option(VITEST, description, vitest(branches));
    };
  }

  private List<String> vitest(final Integer branches) {
    final List<String> arguments = new ArrayList<>(List.of(VITEST, "run", "--coverage"));
    if (ownTestConfig) {
      arguments.add("--coverage.thresholds.lines=" + minimum(preset, LINE));
      if (branches != null) {
        arguments.add("--coverage.thresholds.branches=" + branches);
      }
    }
    return command(arguments.toArray(String[]::new));
  }

  /** The Stryker mutation gate; its config file sets the minimum score. */
  InitGateOption stryker() {
    return option(
        "stryker", "Mutation-tests the project with Stryker (ci).", command("stryker", "run"));
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
