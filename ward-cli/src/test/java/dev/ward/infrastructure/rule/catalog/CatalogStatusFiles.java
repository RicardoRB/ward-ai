package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads the status of the catalog rows per language. Each language keeps its own file {@code
 * catalog/status/<language>/<catalog file>.toml}, so that two pull requests that flip the same row
 * in different languages do not touch the same lines. A line is {@code row-name = "implemented"} or
 * {@code row-name = { status = "planned", issue = "#N" }}, sorted by row name. A file that is
 * absent says nothing about its language.
 */
final class CatalogStatusFiles {

  private static final String ROOT = "catalog/status/";

  /** One status line: the status text and the issue of a planned row. */
  record Entry(String status, String issue) {}

  private CatalogStatusFiles() {}

  /** Returns the status lines of the catalog file {@code stem}, by row name and language. */
  static Map<String, Map<AnalysisLanguage, Entry>> read(final String stem) {
    final Map<String, Map<AnalysisLanguage, Entry>> rows = new LinkedHashMap<>();
    for (final AnalysisLanguage language : AnalysisLanguage.values()) {
      final String resource = ROOT + CatalogRow.key(language) + "/" + stem + ".toml";
      readFile(resource).forEach((name, entry) -> languageRows(rows, name).put(language, entry));
    }
    return rows;
  }

  /** Returns the status lines of the text scope of the catalog file {@code stem}, by row name. */
  static Map<String, Entry> readText(final String stem) {
    return readFile(ROOT + "text/" + stem + ".toml");
  }

  private static Map<String, Entry> readFile(final String resource) {
    final Map<String, Entry> entries = new LinkedHashMap<>();
    try (InputStream stream = CatalogStatusFiles.class.getResourceAsStream("/" + resource)) {
      if (stream == null) {
        return entries;
      }
      final TomlParseResult toml =
          Toml.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      if (toml.hasErrors()) {
        throw new RuleConfigurationException(
            "Catalog status file '"
                + resource
                + "' is not valid TOML: "
                + toml.errors().getFirst());
      }
      for (final String name : toml.keySet()) {
        entries.put(name, entry(resource, name, toml));
      }
    } catch (final IOException exception) {
      throw new RuleConfigurationException(
          "Catalog status file '" + resource + "' could not be read", exception);
    }
    return entries;
  }

  private static Map<AnalysisLanguage, Entry> languageRows(
      final Map<String, Map<AnalysisLanguage, Entry>> rows, final String name) {
    return rows.computeIfAbsent(name, key -> new EnumMap<>(AnalysisLanguage.class));
  }

  private static Entry entry(final String resource, final String name, final TomlParseResult toml) {
    final Object value = toml.get(List.of(name));
    if (value instanceof String status) {
      return new Entry(status, "");
    }
    if (value instanceof TomlTable table) {
      final String status = table.getString("status");
      final String issue = table.getString("issue");
      if (status != null) {
        return new Entry(status, issue == null ? "" : issue);
      }
    }
    throw new RuleConfigurationException(
        "Catalog status file '"
            + resource
            + "', row '"
            + name
            + "' must be a status text or { status, issue }");
  }
}
