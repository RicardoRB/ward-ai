package dev.ward.application.init.node;

import dev.ward.application.init.AgentRulesTemplate;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** The agent rule lines of a Node project's tool gates, part of {@link AgentRulesTemplate}. */
public final class NodeAgentRules {
  /** The rule for a workspace root, naming its runner as the runner shows it. */
  private static String workspaceRule(final String runnerName) {
    return "- This is a "
        + runnerName
        + " workspace. Run the gates through `ward check`, not per package: each gate runs the"
        + " workspace runner for every package, and a package script exists only so the runner can"
        + " run it.";
  }

  private NodeAgentRules() {}

  /**
   * Returns one line per selected Node tool gate, naming its command; the coverage minimums are the
   * ones of the {@code coverage} check in {@code ward.toml}.
   */
  public static List<String> lines(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Set<String> owned) {
    final boolean bun = gates.stream().anyMatch(gate -> NodeTools.BUN_TEST.equals(gate.id()));
    final String coverage = nodeCoverage(preset, !bun);
    final String tests = ". New code needs tests that assert behavior.";
    final Map<String, String> rows =
        Map.of(
            "tsc",
            "- TypeScript compiler: `{command}` must report no errors.",
            NodeTools.VITEST,
            "- Tests (Vitest): `{command}`." + tests,
            NodeTools.JEST,
            "- Tests (Jest): `{command}`." + tests,
            NodeTools.BUN_TEST,
            "- Tests (Bun): `{command}`." + tests,
            "coverage",
            "- Coverage: the `coverage` check reads `coverage/lcov.info` and requires "
                + coverage
                + ", set in `ward.toml`.",
            "stryker",
            "- Mutation testing (Stryker): `{command}` in `ci`"
                + strykerMinimum(preset, owned)
                + ".");
    final List<String> lines =
        Stream.concat(
                Stream.of(wardRule(preset)),
                gates.stream()
                    .filter(gate -> rows.containsKey(gate.id()))
                    .map(
                        gate ->
                            rows.get(gate.id())
                                .replace("{command}", String.join(" ", gate.command()))))
            .toList();
    if (!project.taskRunner().isRootRunner()) {
      return lines;
    }
    return Stream.concat(
            Stream.of(workspaceRule(project.taskRunner().displayName().orElseThrow())),
            lines.stream())
        .toList();
  }

  /** The rule line for Ward's TypeScript and JavaScript rules, which the {@code ward} gate runs. */
  private static String wardRule(final QualityPreset preset) {
    return "- TypeScript and JavaScript: the `ward` check applies the Ward "
        + (preset == null ? QualityPreset.STANDARD : preset).id()
        + " rules selected in `ward-rules.toml`; fix each finding at its cause.";
  }

  /** The preset's coverage minimum; Bun measures no branches, so it gets only the line minimum. */
  private static String nodeCoverage(final QualityPreset preset, final boolean branches) {
    final Integer branch = branches ? NodeTools.minimum(preset, NodeTools.BRANCH) : null;
    return "at least "
        + NodeTools.minimum(preset, NodeTools.LINE)
        + "% line"
        + (branch == null ? "" : " and " + branch + "% branch")
        + " coverage";
  }

  /** Names the break threshold of Ward's Stryker config; a project's own config sets its own. */
  private static String strykerMinimum(final QualityPreset preset, final Set<String> owned) {
    final Integer minimum = preset == null ? null : preset.rules().mutationThreshold();
    final boolean ownConfig =
        owned.stream()
            .anyMatch(file -> file.startsWith("stryker.") || file.startsWith(".stryker."));
    return minimum == null || ownConfig
        ? ""
        : ", with a mutation score of at least " + minimum + "% set in `stryker.config.mjs`";
  }
}
