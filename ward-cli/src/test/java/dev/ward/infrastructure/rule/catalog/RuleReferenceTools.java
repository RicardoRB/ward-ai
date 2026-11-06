package dev.ward.infrastructure.rule.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Formats the tool names and the counterpart cells of the generated rule reference. */
final class RuleReferenceTools {

  private static final Map<String, String> TOOL_NAMES =
      Map.ofEntries(
          Map.entry("checkstyle", "Checkstyle"),
          Map.entry("pmd", "PMD"),
          Map.entry("spotbugs", "SpotBugs"),
          Map.entry("errorprone", "Error Prone"),
          Map.entry("nullaway", "NullAway"),
          Map.entry("semgrep", "Semgrep"),
          Map.entry("eslint", "ESLint"),
          Map.entry("typescript-eslint", "typescript-eslint"),
          Map.entry("angular-eslint", "angular-eslint"),
          Map.entry("biome", "Biome"),
          Map.entry("ruff", "Ruff"),
          Map.entry("pylint", "Pylint"),
          Map.entry("swiftlint", "SwiftLint"),
          Map.entry("detekt", "detekt"),
          Map.entry("ktlint", "ktlint"),
          Map.entry("android-lint", "Android Lint"),
          Map.entry("knip", "knip"),
          Map.entry("vulture", "vulture"),
          Map.entry("periphery", "Periphery"),
          Map.entry("cpd", "PMD CPD"),
          Map.entry("jscpd", "jscpd"),
          Map.entry("gitleaks", "Gitleaks"));

  private RuleReferenceTools() {}

  /** Returns the row of the per-tool count table. */
  static String toolRow(
      final String tool,
      final RuleCatalog catalog,
      final Map<String, Map<String, String>> outOfScope) {
    long implemented = 0;
    long planned = 0;
    for (final CatalogRow row : catalog.rows()) {
      final int count = row.counterparts().getOrDefault(tool, List.of()).size();
      if (row.languages(CatalogStatus.IMPLEMENTED).isEmpty()) {
        planned += count;
      } else {
        implemented += count;
      }
    }
    return String.join(
        " | ",
        "| " + TOOL_NAMES.get(tool),
        UpstreamLists.version(tool),
        String.valueOf(implemented),
        String.valueOf(planned),
        outOfScope.getOrDefault(tool, Map.of()).size() + " |");
  }

  static String counterparts(final CatalogRow row) {
    final List<String> parts = new ArrayList<>();
    for (final String tool : CatalogTools.ALL) {
      final List<String> rules = row.counterparts().getOrDefault(tool, List.of());
      if (!rules.isEmpty()) {
        parts.add(TOOL_NAMES.get(tool) + " " + codes(rules));
      }
    }
    return parts.isEmpty() ? "none" : String.join("; ", parts);
  }

  private static String codes(final List<String> rules) {
    return rules.stream()
        .map(rule -> "`" + rule.replace("|", "\\|") + "`")
        .collect(Collectors.joining(", "));
  }
}
