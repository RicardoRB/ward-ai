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
          Map.entry("gitleaks", "Gitleaks"),
          Map.entry("spectral", "Spectral"));

  private RuleReferenceTools() {}

  /** Returns the row of the tool table: the tool's name and its pinned version. */
  static String versionRow(final String tool) {
    return "| " + TOOL_NAMES.get(tool) + " | " + UpstreamLists.version(tool) + " |";
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
