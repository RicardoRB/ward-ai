package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.FormatGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.QualityPreset.PresetRules;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Renders the {@code AGENTS.md} section that tells coding agents the limits the selected gates
 * enforce, so they meet them before the first check instead of after it fails.
 */
public final class AgentRulesTemplate {
  static final String BEGIN = "<!-- ward-owned: agent-rules v1 begin -->";
  static final String END = "<!-- ward-owned: agent-rules v1 end -->";
  private static final int PERCENT = 100;

  private AgentRulesTemplate() {}

  /**
   * Returns the section for {@code gates}. Limits come from {@code preset}; a rule file in {@code
   * userOwned} is the user's, so the section points to it instead of quoting preset values.
   */
  public static OwnedSection render(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final List<Path> userOwned) {
    final Set<String> ids = gates.stream().map(InitGateOption::id).collect(Collectors.toSet());
    final Set<String> owned =
        userOwned.stream()
            .map(file -> project.root().relativize(file.toAbsolutePath()).toString())
            .collect(Collectors.toSet());
    final List<String> lines = new ArrayList<>();
    lines.add("## Ward quality gates");
    lines.add("");
    lines.add(
        "`ward.toml` defines the checks: "
            + gates.stream().map(gate -> "`" + gate.id() + "`").collect(Collectors.joining(", "))
            + ".");
    lines.add("");
    lines.add(
        "- Fix the cause of every finding. Do not add suppressions or exclusions, disable"
            + " rules, or lower thresholds to make a gate pass.");
    lines.add(
        "- Run `ward check` after changes and `ward check --profile ci` before" + " finishing.");
    // Python's coverage gate is not JaCoCo, so the Maven limit lines do not apply to it.
    if (preset != null
        && project.toolchain() != Toolchain.PYTHON
        && project.toolchain() != Toolchain.SWIFT) {
      lines.addAll(limits(ids, preset.rules()));
    }
    for (final Language language : List.of(Language.JAVA, Language.PYTHON)) {
      if (project.languages().contains(language)) {
        lines.add(
            "- "
                + language.displayName()
                + ": the `ward` check applies the Ward "
                + (preset == null ? QualityPreset.STANDARD : preset).id()
                + " rules selected in `ward-rules.toml`; fix each finding at its cause.");
      }
    }
    lines.add(
        "- Architecture: `ward:architecture/layers` enforces the layer boundaries in"
            + " `ward.toml`; do not remove them or add exemptions.");
    lines.add(
        "- Architecture: when `ward.toml` configures `[checks.ward.cycles]` or"
            + " `[[checks.ward.independence]]`, `ward:architecture/cycles` and"
            + " `ward:architecture/independence` enforce them; do not remove them or add"
            + " exemptions.");
    if (ids.contains(SupportedQualityGates.CRAP)) {
      lines.add(
          "- CRAP (`crap`): split or test every function the check reports; never raise `max` or"
              + " exclude files to pass it.");
    }
    if (ids.contains(FormatGates.WARD_FORMAT)) {
      lines.add(
          "- Format: `ward-format` must pass; format edited files with `ward format`, whose layout"
              + " comes from `.editorconfig`.");
    }
    InitToolchains.all()
        .forEach(support -> lines.addAll(support.agentRules(project, gates, preset, owned)));
    // Node projects keep blank lines around the markers, as their Markdown did under Prettier.
    final String padding = project.toolchain() == Toolchain.NODE ? "\n" : "";
    return new OwnedSection(
        project.root().resolve("AGENTS.md"),
        BEGIN,
        END,
        padding + String.join("\n", lines) + "\n" + padding,
        false);
  }

  /** Returns the gates that can get a limit line, for the gate contract test. */
  static Set<String> limitGates() {
    return limitLines(QualityPreset.STRICT.rules()).keySet();
  }

  /** Returns one line per selected gate that has a limit worth stating, in gate order. */
  private static List<String> limits(final Set<String> ids, final PresetRules rules) {
    return limitLines(rules).entrySet().stream()
        .filter(entry -> ids.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .toList();
  }

  /** Returns the limit line of each gate that has one, keyed by gate ID, in gate order. */
  private static Map<String, String> limitLines(final PresetRules rules) {
    final Map<String, String> lines = new LinkedHashMap<>();
    lines.put(
        "coverage",
        "- Coverage (JaCoCo): " + coverage(rules) + ". New code needs tests that assert behavior.");
    if (rules.mutationThreshold() != null) {
      lines.put(
          "mutation",
          "- Mutation (PIT): at least " + rules.mutationThreshold() + "% of mutants killed.");
    }
    lines.put("enforcer", "- Enforcer: " + String.join(", ", rules.enforcerRules()) + ".");
    return lines;
  }

  private static String coverage(final PresetRules rules) {
    return new TreeMap<>(rules.jacocoMinimums())
        .entrySet().stream()
            .map(
                entry ->
                    entry.getKey().toLowerCase(Locale.ROOT)
                        + " at least "
                        + Math.round(entry.getValue() * PERCENT)
                        + "%")
            .collect(Collectors.joining(", "));
  }
}
