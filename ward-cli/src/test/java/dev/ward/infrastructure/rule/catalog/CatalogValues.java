package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.Locale;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

/**
 * Small readers for the values of a catalog row; a problem is an {@link IllegalArgumentException}.
 */
final class CatalogValues {

  private CatalogValues() {}

  /** Returns {@code value}, or fails when it is missing or blank. */
  static String required(final String value, final String key) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("'" + key + "' must be a non-blank string");
    }
    return value;
  }

  /** Returns {@code value}, or the empty text when it is missing. */
  static String optional(final String value) {
    return value == null ? "" : value;
  }

  /** Returns the engine language named {@code key}. */
  static AnalysisLanguage language(final String key) {
    try {
      return AnalysisLanguage.valueOf(key.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException exception) {
      throw new IllegalArgumentException("'" + key + "' is not an engine language", exception);
    }
  }

  /** Returns the table at {@code key}, or an empty table when the row has none. */
  static TomlTable table(final TomlTable row, final String key) {
    final TomlTable table = row.getTable(key);
    return table == null ? Toml.parse("") : table;
  }
}
