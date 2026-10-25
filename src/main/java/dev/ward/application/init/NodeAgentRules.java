package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The agent rule lines of a Node project's tool gates, part of {@link AgentRulesTemplate}. */
final class NodeAgentRules {
  private NodeAgentRules() {}

  /**
   * Returns one line per selected Node tool gate, naming its command; the minimums are the ones the
   * test runner and Stryker configs or commands enforce.
   */
  static List<String> lines(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Set<String> owned) {
    final String coverage = nodeCoverage(preset, true);
    final String tests = ". New code needs tests that assert behavior.";
    final Map<String, String> rows =
        Map.of(
            "tsc",
            "- TypeScript compiler: `{command}` must report no errors.",
            "eslint",
            "- ESLint: " + eslintRules(project, preset, owned) + "; the gate runs `{command}`.",
            "prettier",
            "- Prettier: format edited files with Prettier and the project's config; the gate"
                + " runs `{command}`.",
            "biome",
            "- Biome: "
                + biomeRules(project, preset, owned)
                + "; format and fix edited files with `biome check --write`, and the gate runs"
                + " `{command}`.",
            NodeTools.VITEST,
            "- Tests (Vitest): `{command}`, " + coverage + tests,
            NodeTools.JEST,
            "- Tests (Jest): `{command}`, " + coverage + tests,
            NodeTools.BUN_TEST,
            "- Tests (Bun): `{command}`, "
                + nodeCoverage(preset, false)
                + " set in `bunfig.toml`"
                + tests,
            "stryker",
            "- Mutation testing (Stryker): `{command}` in `ci`"
                + strykerMinimum(preset, owned)
                + ".",
            "dependency-cruiser",
            "- Dependency rules (dependency-cruiser): `{command}` must pass. Fix the import that"
                + " breaks an architecture rule; do not edit or weaken the `.dependency-cruiser.*`"
                + " rules.");
    return gates.stream()
        .filter(gate -> rows.containsKey(gate.id()))
        .map(gate -> rows.get(gate.id()).replace("{command}", String.join(" ", gate.command())))
        .toList();
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

  private static String eslintRules(
      final Project project, final QualityPreset preset, final Set<String> owned) {
    final String rules;
    if (project.language() == Language.JAVASCRIPT) {
      rules = "ESLint's recommended JavaScript rules";
    } else if (preset == QualityPreset.STRICT) {
      rules = "typescript-eslint's `strict-type-checked` rules";
    } else {
      rules = "typescript-eslint's recommended rules";
    }
    return owned.stream()
        .filter(file -> file.startsWith("eslint.config.") || file.startsWith(".eslintrc"))
        .findFirst()
        .map(file -> "follow the rules in `" + file + "`")
        .orElse("Ward's flat config (`eslint.config.*`) with " + rules);
  }

  /**
   * Names the Biome config in use and the rules it enforces. Under strict, warnings fail the gate,
   * and for TypeScript Biome's type-aware rules replace typescript-eslint's strict rules.
   */
  private static String biomeRules(
      final Project project, final QualityPreset preset, final Set<String> owned) {
    final String config =
        owned.stream()
            .filter(file -> file.startsWith("biome.") || file.startsWith(".biome."))
            .findFirst()
            .map(file -> "follow the rules in `" + file + "`")
            .orElse(
                "Ward's `biome.jsonc` with Biome's recommended rules"
                    + (preset == QualityPreset.STRICT ? " and Ward's strict rules" : ""));
    if (preset != QualityPreset.STRICT) {
      return config;
    }
    final String typeAware =
        project.language() == Language.TYPESCRIPT
            ? "; Biome's type-aware rules replace typescript-eslint's `strict-type-checked`"
            : "";
    return config + "; warnings fail the gate" + typeAware;
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
