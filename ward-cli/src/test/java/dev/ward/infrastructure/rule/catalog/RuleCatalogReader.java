package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads the rule catalog: the files that {@code catalog/files.txt} lists, each with one {@code
 * [[rule]]} table per rule concept. A file that cannot be used makes the whole catalog unusable,
 * because a partial catalog would hide rules.
 */
public final class RuleCatalogReader {

  private static final Logger LOG = Logger.getLogger(RuleCatalogReader.class.getName());
  private static final String DIRECTORY = "catalog/";
  private static final String INDEX = DIRECTORY + "files.txt";

  /** Reads every catalog file of the Ward distribution. */
  public RuleCatalog load() {
    final List<CatalogRow> rows = new ArrayList<>();
    for (final String file : lines(INDEX)) {
      rows.addAll(
          readFile(
              DIRECTORY + file + ".toml",
              CatalogStatusFiles.read(file),
              CatalogStatusFiles.readText(file)));
    }
    LOG.fine(() -> "read " + rows.size() + " catalog rows");
    return new RuleCatalog(rows);
  }

  /**
   * Parses one catalog file.
   *
   * @param source the name used in messages
   * @param reader the TOML text
   * @throws RuleConfigurationException if the file or one of its rows is not valid
   */
  public List<CatalogRow> parse(final String source, final Reader reader) throws IOException {
    return parse(source, reader, Map.of(), Map.of());
  }

  private List<CatalogRow> parse(
      final String source,
      final Reader reader,
      final Map<String, Map<AnalysisLanguage, CatalogStatusFiles.Entry>> statuses,
      final Map<String, CatalogStatusFiles.Entry> textStatuses)
      throws IOException {
    final TomlParseResult toml = Toml.parse(reader);
    if (toml.hasErrors()) {
      throw new RuleConfigurationException(
          "Catalog file '" + source + "' is not valid TOML: " + toml.errors().getFirst());
    }
    final TomlArray tables = toml.getArray("rule");
    final List<CatalogRow> rows = new ArrayList<>();
    for (int index = 0; tables != null && index < tables.size(); index++) {
      rows.add(row(source, tables.getTable(index), statuses, textStatuses));
    }
    rejectUnknownRows(source, rows, statuses.keySet());
    rejectUnknownRows(source, rows, textStatuses.keySet());
    return rows;
  }

  private List<CatalogRow> readFile(
      final String resource,
      final Map<String, Map<AnalysisLanguage, CatalogStatusFiles.Entry>> statuses,
      final Map<String, CatalogStatusFiles.Entry> textStatuses) {
    try (Reader reader = new InputStreamReader(open(resource), StandardCharsets.UTF_8)) {
      return parse(resource, reader, statuses, textStatuses);
    } catch (final IOException exception) {
      throw new RuleConfigurationException(
          "Catalog file '" + resource + "' could not be read", exception);
    }
  }

  private static void rejectUnknownRows(
      final String source, final List<CatalogRow> rows, final Set<String> statusNames) {
    final Set<String> names = rows.stream().map(CatalogRow::name).collect(Collectors.toSet());
    for (final String name : statusNames) {
      if (!names.contains(name)) {
        throw new RuleConfigurationException(
            "Catalog file '" + source + "' has no row '" + name + "' but a status file names it");
      }
    }
  }

  private static CatalogRow row(
      final String source,
      final TomlTable table,
      final Map<String, Map<AnalysisLanguage, CatalogStatusFiles.Entry>> statuses,
      final Map<String, CatalogStatusFiles.Entry> textStatuses) {
    final String name = table.getString("name");
    try {
      return new CatalogRow(
          CatalogValues.required(name, "name"),
          CatalogValues.optional(table.getString("category")),
          RuleRowPresets.read(table),
          CatalogTables.statuses(table, statuses.getOrDefault(name, Map.of())),
          CatalogTables.ids(table),
          CatalogTables.counterparts(table),
          source,
          CatalogStatusFiles.textStatus(table, textStatuses.get(name)),
          CatalogTables.strings(table, "cwe"),
          CatalogTables.strings(table, "sources"));
    } catch (final IllegalArgumentException exception) {
      throw new RuleConfigurationException(
          "Catalog file '"
              + source
              + "', rule '"
              + name
              + "' cannot be used: "
              + exception.getMessage(),
          exception);
    }
  }

  private static List<String> lines(final String resource) {
    try (InputStream stream = open(resource)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .filter(line -> !line.isBlank())
          .toList();
    } catch (final IOException exception) {
      throw new RuleConfigurationException(
          "Catalog index '" + resource + "' could not be read", exception);
    }
  }

  private static InputStream open(final String resource) {
    final InputStream stream = RuleCatalogReader.class.getResourceAsStream("/" + resource);
    if (stream == null) {
      throw new RuleConfigurationException(
          "Catalog file '" + resource + "' is missing from the Ward distribution");
    }
    return stream;
  }
}
