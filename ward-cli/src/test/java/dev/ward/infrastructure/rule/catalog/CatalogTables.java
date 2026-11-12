package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Reads the tables of one catalog row: {@code status} with {@code issues} and {@code
 * not-applicable}, {@code ids} and the counterpart lists. Every problem is an {@link
 * IllegalArgumentException} whose message the reader attaches to the file and the rule.
 */
final class CatalogTables {

  private static final String STATUS = "status";
  private static final String ISSUES = "issues";
  private static final String NOT_APPLICABLE = "not-applicable";
  private static final String IDS = "ids";
  private static final Set<String> RESERVED =
      Set.of("name", "category", "presets", "scope", STATUS, ISSUES, NOT_APPLICABLE, IDS);

  private CatalogTables() {}

  /** Returns the status per language, joining each status with its issue or reason. */
  static Map<AnalysisLanguage, LanguageStatus> statuses(final TomlTable row) {
    return statuses(row, Map.of());
  }

  /**
   * Returns the status per language, from the row's own {@code status} table and from the status
   * files, in the order of {@link AnalysisLanguage}. A language that both name is rejected.
   */
  static Map<AnalysisLanguage, LanguageStatus> statuses(
      final TomlTable row, final Map<AnalysisLanguage, CatalogStatusFiles.Entry> external) {
    final TomlTable status = CatalogValues.table(row, STATUS);
    final TomlTable issues = CatalogValues.table(row, ISSUES);
    final TomlTable reasons = CatalogValues.table(row, NOT_APPLICABLE);
    final Map<AnalysisLanguage, CatalogStatusFiles.Entry> all =
        new EnumMap<>(AnalysisLanguage.class);
    for (final String key : status.keySet()) {
      all.put(CatalogValues.language(key), entryOf(key, status, issues));
    }
    external.forEach(
        (language, entry) -> {
          if (all.put(language, entry) != null) {
            throw new IllegalArgumentException(
                "the status of "
                    + CatalogRow.key(language)
                    + " is in the row and in a status file");
          }
        });
    final Map<AnalysisLanguage, LanguageStatus> statuses = new EnumMap<>(AnalysisLanguage.class);
    all.forEach(
        (language, entry) ->
            statuses.put(language, languageStatus(CatalogRow.key(language), entry, reasons)));
    rejectUnlisted(issues, status, ISSUES);
    rejectUnlisted(reasons, all);
    return statuses;
  }

  /** Returns the ids that differ from {@code ward:<language>/<name>}. */
  static Map<AnalysisLanguage, String> ids(final TomlTable row) {
    final TomlTable table = CatalogValues.table(row, IDS);
    final Map<AnalysisLanguage, String> ids = new LinkedHashMap<>();
    for (final String key : table.keySet()) {
      ids.put(
          CatalogValues.language(key),
          CatalogValues.required(table.getString(key), IDS + "." + key));
    }
    return ids;
  }

  /** Returns the upstream rule ids per tool, rejecting a key that is no tool and no row key. */
  static Map<String, List<String>> counterparts(final TomlTable row) {
    final Map<String, List<String>> counterparts = new LinkedHashMap<>();
    for (final String key : row.keySet()) {
      if (CatalogTools.ALL.contains(key)) {
        counterparts.put(key, strings(row.get(key), key));
      } else if (!RESERVED.contains(key)) {
        throw new IllegalArgumentException("unknown key '" + key + "'");
      }
    }
    return counterparts;
  }

  private static CatalogStatusFiles.Entry entryOf(
      final String key, final TomlTable status, final TomlTable issues) {
    return new CatalogStatusFiles.Entry(
        status.getString(key), CatalogValues.optional(issues.getString(key)));
  }

  private static LanguageStatus languageStatus(
      final String key, final CatalogStatusFiles.Entry entry, final TomlTable reasons) {
    final CatalogStatus value =
        CatalogStatus.byKey(entry.status()).orElseThrow(() -> unknownStatus(key));
    return new LanguageStatus(value, entry.issue(), CatalogValues.optional(reasons.getString(key)));
  }

  private static IllegalArgumentException unknownStatus(final String key) {
    return new IllegalArgumentException("unknown status for " + key);
  }

  private static void rejectUnlisted(
      final TomlTable table, final TomlTable status, final String name) {
    for (final String key : table.keySet()) {
      if (!status.contains(key)) {
        throw new IllegalArgumentException("'" + name + "' names " + key + " with no status");
      }
    }
  }

  private static void rejectUnlisted(
      final TomlTable reasons, final Map<AnalysisLanguage, CatalogStatusFiles.Entry> all) {
    for (final String key : reasons.keySet()) {
      if (!all.containsKey(CatalogValues.language(key))) {
        throw new IllegalArgumentException(
            "'" + NOT_APPLICABLE + "' names " + key + " with no status");
      }
    }
  }

  private static List<String> strings(final Object value, final String key) {
    if (!(value instanceof TomlArray array)) {
      throw new IllegalArgumentException("'" + key + "' must be a list of strings");
    }
    final List<String> values = new ArrayList<>();
    for (int index = 0; index < array.size(); index++) {
      values.add(CatalogValues.required(array.getString(index), key));
    }
    return values;
  }
}
