package dev.ward.infrastructure.init.node;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Framework;

/** Writes Ward's Stryker config, whose break threshold and mutated files follow the preset. */
public final class NodeStrykerTemplate {
  private static final String STRYKER =
      """
      export default {
      {runner}
      {mutate}  reporters: ["json", "clear-text", "progress"],
        thresholds: {
          high: 80,
          low: 60,
      {break}  },
      };
      """;

  // Stryker has no Bun runner plugin; its command runner runs the whole suite per mutant.
  private static final String BUN_RUNNER =
      """
        testRunner: "command",
        commandRunner: {
          command: "bun test",
        },
        coverageAnalysis: "off",
      """
          .stripTrailing();

  private NodeStrykerTemplate() {}

  /**
   * Returns a Stryker config for {@code testGate} with the {@code json} reporter Ward reads. Like
   * PIT, it fails below a minimum score only for a preset that sets a mutation threshold. A Vue,
   * Svelte or Astro project mutates script files only: Stryker's default glob includes component
   * files, and instrumenting them breaks their script blocks. Limiting the glob to `src` also keeps
   * tests and config files out of the mutants.
   */
  public static String stryker(
      final String testGate, final QualityPreset preset, final Framework framework) {
    final Integer minimum = preset == null ? null : preset.rules().mutationThreshold();
    final String runner =
        "bun-test".equals(testGate) ? BUN_RUNNER : "  testRunner: \"" + testGate + "\",";
    return STRYKER
        .replace("{runner}", runner)
        .replace("{mutate}", mutate(framework))
        .replace("{break}", minimum == null ? "" : "    break: " + minimum + ",\n");
  }

  private static String mutate(final Framework framework) {
    return switch (framework) {
      case VUE, SVELTE, ASTRO -> "  mutate: [\"src/**/*.{js,ts,jsx,tsx,mjs,mts,cts,cjs}\"],\n";
      default -> "";
    };
  }
}
