package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.TomlValues;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleConfigurationException;
import dev.ward.analysis.domain.rule.check.ModifierTable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads the modifier table each language module ships as {@code rules/modifiers/<language>.toml}:
 * {@code kinds}, the node kinds that hold modifiers, and a {@code [texts]} table from modifier name
 * to token texts. A language without a file has no table.
 */
final class ModifierTables {

  private ModifierTables() {}

  /** Returns the table of the one language a rule covers; a rule over several has none. */
  static ModifierTable forLanguages(final Set<AnalysisLanguage> languages) {
    return languages.size() == 1 ? forLanguage(languages.iterator().next()) : ModifierTable.NONE;
  }

  /**
   * Returns the table of {@code language}.
   *
   * @throws RuleConfigurationException if the language's file is not a valid table
   */
  static ModifierTable forLanguage(final AnalysisLanguage language) {
    final String resource =
        "/rules/modifiers/" + language.name().toLowerCase(Locale.ROOT) + ".toml";
    try (InputStream stream = ModifierTables.class.getResourceAsStream(resource)) {
      return stream == null ? ModifierTable.NONE : read(resource, stream);
    } catch (final IOException exception) {
      throw new RuleConfigurationException(
          "the modifier table " + resource + " is unreadable", exception);
    }
  }

  private static ModifierTable read(final String resource, final InputStream stream)
      throws IOException {
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      final TomlParseResult toml = Toml.parse(reader);
      if (toml.hasErrors()) {
        throw new RuleConfigurationException(
            "the modifier table " + resource + " is not valid TOML");
      }
      return new ModifierTable(TomlValues.strings(toml, "kinds"), texts(toml.getTable("texts")));
    }
  }

  private static Map<String, Set<String>> texts(final TomlTable table) {
    final Map<String, Set<String>> texts = new HashMap<>();
    if (table != null) {
      table
          .keySet()
          .forEach(name -> texts.put(name, TomlValues.stringList(table.getArray(name), name)));
    }
    return texts;
  }
}
