package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The conditions of the security catalog: every row of {@code catalog/security.toml} names CWE ids
 * of the pinned list and at least one source, and every rule of category {@code security} has a
 * row.
 */
final class SecurityCatalogRules {

  /** The catalog file that holds the security rows. */
  static final String FILE = "catalog/security.toml";

  private static final String CWE_LIST = "/catalog/security/cwe-5.0.txt";
  private static final String CATEGORY = "security";

  private SecurityCatalogRules() {}

  /** Returns the CWE ids of the pinned release, such as {@code CWE-89}. */
  static Set<String> pinnedCweIds() {
    try (InputStream stream = SecurityCatalogRules.class.getResourceAsStream(CWE_LIST)) {
      final Set<String> ids = new HashSet<>();
      new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> !line.startsWith("#") && !line.isBlank())
          .forEach(line -> ids.add(line.split("\t", 2)[0]));
      return ids;
    } catch (final IOException exception) {
      throw new IllegalStateException("the pinned CWE list cannot be read", exception);
    }
  }

  /** Returns what is wrong with the security rows and with the rules of category security. */
  static List<String> problems(
      final List<CatalogRow> rows, final Set<String> cweIds, final List<RuleDefinition> rules) {
    final List<String> problems = new ArrayList<>();
    for (final CatalogRow row : rows) {
      if (FILE.equals(row.source())) {
        problems.addAll(rowProblems(row, cweIds));
      }
    }
    final Set<String> owned = new HashSet<>();
    rows.forEach(row -> owned.addAll(row.implementedIds()));
    for (final RuleDefinition rule : rules) {
      if (CATEGORY.equals(rule.category()) && !owned.contains(rule.id())) {
        problems.add("rule " + rule.id() + " has no catalog row");
      }
    }
    return problems;
  }

  private static List<String> rowProblems(final CatalogRow row, final Set<String> cweIds) {
    final List<String> problems = new ArrayList<>();
    if (row.cwe().isEmpty()) {
      problems.add("row " + row.name() + " has no cwe");
    }
    if (row.sources().isEmpty()) {
      problems.add("row " + row.name() + " has no sources");
    }
    for (final String id : row.cwe()) {
      if (!cweIds.contains(id)) {
        problems.add("row " + row.name() + " names " + id + ", which is not in the pinned list");
      }
    }
    for (final String source : row.sources()) {
      if (!source.startsWith("https://")) {
        problems.add("row " + row.name() + " has a source that is not an https URL: " + source);
      }
    }
    return problems;
  }
}
