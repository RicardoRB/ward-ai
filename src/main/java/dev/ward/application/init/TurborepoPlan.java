package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.ScriptLine;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.PackageManifest;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.WorkspaceMember;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Plans a Turborepo workspace. Each per-source gate of the root runs its turbo task, and each
 * member that holds sources gets its own command as a package.json script of that task's name.
 * Repository-wide gates stay single runs from the root.
 */
final class TurborepoPlan {
  static final String CHECK_TYPES = "check-types";
  static final String LINT = "lint";
  static final String TEST = "test";
  static final String MUTATION = "mutation";
  private static final List<String> TURBO_FLAGS =
      List.of("--log-prefix=task", "--log-order=grouped", "--continue=always", "--ui=stream");

  private static final Map<String, String> TASK_BY_GATE =
      Map.ofEntries(
          Map.entry("tsc", CHECK_TYPES),
          Map.entry("vue-tsc", CHECK_TYPES),
          Map.entry("svelte-check", CHECK_TYPES),
          Map.entry("astro-check", CHECK_TYPES),
          Map.entry("ngc", CHECK_TYPES),
          Map.entry("eslint", LINT),
          Map.entry("biome", LINT),
          Map.entry(NodeTools.VITEST, TEST),
          Map.entry(NodeTools.JEST, TEST),
          Map.entry(NodeTools.BUN_TEST, TEST),
          Map.entry(NodeTools.NG_TEST, TEST),
          Map.entry("stryker", MUTATION));

  /** The test runners in tie-break order when members use different ones. */
  private static final List<String> TEST_RUNNERS =
      List.of(NodeTools.VITEST, NodeTools.JEST, NodeTools.BUN_TEST, NodeTools.NG_TEST);

  private TurborepoPlan() {}

  /** One member's package.json scripts, by the turbo task each one runs. */
  /**
   * One member's gates and the scripts init writes for it, with the scripts it has now.
   *
   * @param project the member inspected as a project
   * @param name the member's package name
   * @param gates the member's gates, with the root's lint tool
   * @param scripts the command init writes for each script, by script name
   * @param current the command each existing script runs now, by script name
   */
  record MemberPlan(
      Project project,
      String name,
      List<InitGateOption> gates,
      Map<String, String> scripts,
      Map<String, String> current) {
    /** Copies the gates and scripts. */
    MemberPlan {
      gates = List.copyOf(gates);
      scripts = Map.copyOf(scripts);
      current = Map.copyOf(current);
    }
  }

  /** Returns the turbo task that runs the gate {@code id}, or empty for a gate that stays put. */
  static Optional<String> taskOf(final String id) {
    return Optional.ofNullable(TASK_BY_GATE.get(id));
  }

  /** Returns the workspace members that hold sources, which are the ones init plans. */
  static List<WorkspaceMember> plannable(final Project root, final InitInputs inputs) {
    return inputs.members().members(root.root()).stream()
        .filter(member -> inputs.sources().hasSourceTree(member.directory()))
        .toList();
  }

  /**
   * Returns the test runner gate the root names: the one most plannable members use, with ties
   * going to the earlier runner in {@link #TEST_RUNNERS}, or the root's own runner without members.
   */
  static String testGate(final Project root, final InitInputs inputs) {
    final Map<String, Long> counts =
        plannable(root, inputs).stream()
            .map(member -> NodeTools.testGate(member.project(), manifest(member, inputs)))
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    if (counts.isEmpty()) {
      return NodeTools.testGate(root, inputs.manifests().read(root.root()));
    }
    final long most = counts.values().stream().max(Long::compare).orElse(0L);
    return TEST_RUNNERS.stream()
        .filter(runner -> counts.getOrDefault(runner, 0L) == most)
        .findFirst()
        .orElse(NodeTools.VITEST);
  }

  /**
   * Returns the root's analysis gates: each task gate runs {@code turbo run <task>} through the
   * package manager, and the others are unchanged.
   */
  static List<InitGateOption> rootGates(
      final List<InitGateOption> analysis, final BuildTool manager) {
    return analysis.stream()
        .map(gate -> taskOf(gate.id()).map(task -> turboGate(gate, task, manager)).orElse(gate))
        .toList();
  }

  /**
   * Returns each plannable member's scripts. Lint is the root's tool, so a member's lint script
   * matches the root's; the member keeps its own test runner and checker.
   */
  static List<MemberPlan> memberScripts(
      final Project root, final QualityPreset preset, final InitInputs inputs) {
    final NodeLinting linting =
        NodeLinting.of(root, inputs.manifests().read(root.root()), inputs.configs());
    return plannable(root, inputs).stream()
        .map(member -> memberScripts(member, linting, preset, inputs))
        .toList();
  }

  private static MemberPlan memberScripts(
      final WorkspaceMember member,
      final NodeLinting linting,
      final QualityPreset preset,
      final InitInputs inputs) {
    final Project project = member.project();
    final PackageManifest manifest = manifest(member, inputs);
    final NodeTools tools = NodeTools.of(project, manifest, inputs, preset).withLinting(linting);
    NodeInitSupport.refuseBiomeForAngular(project, tools);
    final Map<String, String> scripts = new LinkedHashMap<>();
    final List<InitGateOption> gates =
        NodeInitSupport.languages(project, NodeTools.testGate(project, manifest), linting)
            .analysisGates(tools);
    gates.forEach(
        gate ->
            taskOf(gate.id())
                .ifPresent(
                    task ->
                        scripts.putIfAbsent(
                            task,
                            ScriptLine.of(withoutRunner(gate.command(), project.buildTool())))));
    return new MemberPlan(project, member.name(), gates, scripts, member.scripts());
  }

  private static PackageManifest manifest(final WorkspaceMember member, final InitInputs inputs) {
    return inputs.manifests().read(member.directory());
  }

  /** Returns {@code command} without the package manager's runner prefix. */
  private static List<String> withoutRunner(final List<String> command, final BuildTool manager) {
    return command.subList(NodeTools.runner(manager).size(), command.size());
  }

  /** Returns the command that runs {@code task} for every package through {@code turbo run}. */
  static List<String> turboCommand(final BuildTool manager, final String task) {
    final String[] arguments =
        Stream.concat(Stream.of("turbo", "run", task), TURBO_FLAGS.stream()).toArray(String[]::new);
    return NodeTools.command(manager, arguments);
  }

  private static InitGateOption turboGate(
      final InitGateOption gate, final String task, final BuildTool manager) {
    return new InitGateOption(
        gate.id(),
        gate.description(),
        turboCommand(manager, task),
        gate.available(),
        gate.availabilityMessage(),
        gate.settings());
  }
}
