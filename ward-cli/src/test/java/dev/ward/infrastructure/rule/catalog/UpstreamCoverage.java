package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.rule.catalog.CatalogRow;
import dev.ward.analysis.domain.rule.catalog.RuleCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks that every pinned upstream rule is in exactly one catalog row or listed as out of scope
 * with a reason, and that no row names a rule the pinned list lacks. Returns one message per
 * problem so that the build names every rule to classify.
 */
final class UpstreamCoverage {

  private UpstreamCoverage() {}

  /** Returns the problems of {@code catalog} against the pinned lists and out-of-scope files. */
  static List<String> problems(
      final RuleCatalog catalog,
      final Map<String, Set<String>> pinned,
      final Map<String, Map<String, String>> outOfScope) {
    final List<String> problems = new ArrayList<>();
    for (final Map.Entry<String, Set<String>> tool : pinned.entrySet()) {
      final Set<String> mapped = mapped(catalog, tool.getKey());
      final Map<String, String> excluded = outOfScope.getOrDefault(tool.getKey(), Map.of());
      unclassified(problems, tool, mapped, excluded);
      unknown(problems, catalog, tool, excluded);
    }
    return problems;
  }

  private static void unclassified(
      final List<String> problems,
      final Map.Entry<String, Set<String>> tool,
      final Set<String> mapped,
      final Map<String, String> excluded) {
    for (final String rule : tool.getValue()) {
      final boolean inRow = mapped.contains(rule);
      final boolean excludedRule = excluded.containsKey(rule);
      if (!inRow && !excludedRule) {
        problems.add(
            tool.getKey() + " rule '" + rule + "' is neither in a catalog row nor out of scope");
      }
      if (inRow && excludedRule) {
        problems.add(tool.getKey() + " rule '" + rule + "' is in a catalog row and out of scope");
      }
    }
  }

  private static void unknown(
      final List<String> problems,
      final RuleCatalog catalog,
      final Map.Entry<String, Set<String>> tool,
      final Map<String, String> excluded) {
    final Set<String> pinned = tool.getValue();
    for (final CatalogRow row : catalog.rows()) {
      for (final String rule : row.counterparts().getOrDefault(tool.getKey(), List.of())) {
        if (!pinned.contains(rule)) {
          problems.add(
              "row '"
                  + row.name()
                  + "' names the "
                  + tool.getKey()
                  + " rule '"
                  + rule
                  + "', which is not in the pinned list");
        }
      }
    }
    excluded.forEach(
        (rule, reason) -> {
          if (!pinned.contains(rule)) {
            problems.add(
                tool.getKey() + " out-of-scope rule '" + rule + "' is not in the pinned list");
          }
          if (reason == null || reason.isBlank()) {
            problems.add(tool.getKey() + " out-of-scope rule '" + rule + "' has no reason");
          }
        });
  }

  private static Set<String> mapped(final RuleCatalog catalog, final String tool) {
    final Set<String> mapped = new HashSet<>();
    for (final CatalogRow row : catalog.rows()) {
      mapped.addAll(row.counterparts().getOrDefault(tool, List.of()));
    }
    return mapped;
  }
}
