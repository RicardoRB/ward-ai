package dev.ward.analysis.domain.rule.catalog;

import dev.ward.analysis.domain.rule.RuleConfigurationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every rule concept of Ward, implemented or planned. A name belongs to one row, and an upstream
 * rule belongs to one row, so a rule of a replaced tool is mapped exactly once.
 *
 * @param rows the rows in catalog order
 */
public record RuleCatalog(List<CatalogRow> rows) {

  /**
   * Copies the rows after checking that no name and no upstream rule appears twice.
   *
   * @throws RuleConfigurationException if two rows share a name, or name the same upstream rule
   */
  public RuleCatalog {
    rows = List.copyOf(rows);
    final Map<String, String> names = new HashMap<>();
    final Map<String, String> counterparts = new HashMap<>();
    for (final CatalogRow row : rows) {
      claim(names, row.name(), row, "Rule name '" + row.name() + "'");
      row.counterparts()
          .forEach(
              (tool, rules) ->
                  rules.forEach(
                      rule ->
                          claim(
                              counterparts,
                              tool + "/" + rule,
                              row,
                              "The " + tool + " rule '" + rule + "'")));
    }
  }

  /** Returns the row called {@code name}. */
  public Optional<CatalogRow> row(final String name) {
    return rows.stream().filter(row -> row.name().equals(name)).findFirst();
  }

  private static void claim(
      final Map<String, String> owners,
      final String key,
      final CatalogRow row,
      final String subject) {
    final String previous = owners.put(key, row.name() + " (" + row.source() + ")");
    if (previous != null) {
      throw new RuleConfigurationException(
          subject
              + " is in the catalog twice: in row "
              + previous
              + " and in row "
              + row.name()
              + " ("
              + row.source()
              + "). Keep one row per rule.");
    }
  }
}
