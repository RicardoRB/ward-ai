package dev.ward.application.init.node;

import dev.ward.application.init.InitInputs;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.node.PackageManifest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gate choices that run a Node tool as a local dependency through the project's package manager.
 *
 * @param manager the project's package manager
 * @param testGate the test runner gate: {@code vitest}, {@code jest} or {@code bun-test}
 * @param available whether the package manager is on the path
 * @param preset the selected preset, or {@code null} for a custom selection
 * @param ownTestConfig whether the project keeps its own test runner config, which init leaves
 *     unchanged
 * @param checkSyncsSvelteKit whether the project's {@code check} script runs {@code svelte-kit
 *     sync} before {@code svelte-check}, which needs the sync's {@code .svelte-kit} tsconfig
 */
public record NodeTools(
    BuildTool manager,
    String testGate,
    boolean available,
    QualityPreset preset,
    boolean ownTestConfig,
    boolean checkSyncsSvelteKit) {
  public static final String VITEST = "vitest";
  public static final String JEST = "jest";
  public static final String BUN_TEST = "bun-test";
  public static final String NG_TEST = "ng-test";
  static final String LINE = "LINE";
  static final String BRANCH = "BRANCH";
  private static final String SVELTE_SYNC = "svelte-kit sync";
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
        inputs.configs().userConfig(project.root(), testGate).isPresent(),
        syncsSvelteKit(manifest.check()));
  }

  /** Returns these tools with {@code gate} as the test runner gate. */
  NodeTools withTestGate(final String gate) {
    return new NodeTools(manager, gate, available, preset, ownTestConfig, checkSyncsSvelteKit);
  }

  /**
   * The command of svelte-check: the project's {@code check} script when it runs {@code svelte-kit
   * sync} first, so the tsconfig it extends exists; otherwise svelte-check itself.
   */
  List<String> svelteCheckCommand() {
    return checkSyncsSvelteKit ? scriptCommand("check") : command("svelte-check");
  }

  private static boolean syncsSvelteKit(final String check) {
    return check != null && check.contains(SVELTE_SYNC);
  }

  /** Returns the package manager's command that runs {@code script} from {@code package.json}. */
  private List<String> scriptCommand(final String script) {
    final List<String> command =
        switch (manager) {
          case PNPM -> List.of("pnpm", "run");
          case YARN -> List.of("yarn", "run");
          case BUN -> List.of("bun", "run");
          default -> List.of("npm", "run");
        };
    final List<String> arguments = new ArrayList<>(command);
    arguments.add(script);
    return List.copyOf(arguments);
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
   * The gate of the project's test runner. It writes {@code coverage/lcov.info} and keeps its text
   * table; Ward's own Vitest config does that for Vitest, Jest writes lcov by default, and a
   * project's own config gets reporter flags. No threshold is passed: the {@code coverage} check
   * holds the minimums.
   */
  InitGateOption tests() {
    final String description = "Runs the project's tests with coverage.";
    return switch (testGate) {
      case JEST ->
          option(
              JEST,
              description,
              ownTestConfig
                  ? command(
                      JEST, "--coverage", "--coverageReporters=text", "--coverageReporters=lcov")
                  : command(JEST, "--coverage"));
      case BUN_TEST ->
          option(
              BUN_TEST,
              description,
              List.of(
                  "bun",
                  "test",
                  "--coverage",
                  "--coverage-reporter=text",
                  "--coverage-reporter=lcov"));
      case NG_TEST ->
          option(
              NG_TEST,
              description + " (the project's angular.json sets any coverage threshold.)",
              command("ng", "test", "--watch=false", "--coverage"));
      default ->
          option(
              VITEST,
              description,
              ownTestConfig
                  ? command(
                      VITEST,
                      "run",
                      "--coverage",
                      "--coverage.reporter=text",
                      "--coverage.reporter=lcov")
                  : command(VITEST, "run", "--coverage"));
    };
  }

  /**
   * The {@code coverage} check that follows the test gate: it runs no command and reads the lcov
   * report that gate wrote. Angular's {@code ng test} gets none. Bun measures no branches, so it
   * gets only the line minimum.
   */
  Optional<InitGateOption> coverage() {
    if (NG_TEST.equals(testGate)) {
      return Optional.empty();
    }
    final Map<String, String> settings = new LinkedHashMap<>();
    settings.put("minimum", String.valueOf(minimum(preset, LINE)));
    final Integer branches = minimum(preset, BRANCH);
    if (branches != null && !BUN_TEST.equals(testGate)) {
      settings.put("minimum-branches", String.valueOf(branches));
    }
    return Optional.of(
        new InitGateOption(
            SupportedQualityGates.COVERAGE,
            "Fails below the line and branch coverage minimums in ward.toml.",
            List.of(),
            true,
            "Reads the lcov report that the test gate writes; it runs no command.",
            settings));
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
