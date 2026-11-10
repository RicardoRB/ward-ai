package dev.ward.infrastructure.rule.catalog;

import static dev.ward.infrastructure.rule.catalog.RuleSchemaJson.array;
import static dev.ward.infrastructure.rule.catalog.RuleSchemaJson.obj;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Builds the schema of the {@code [select]} table, with the enums the selector accepts. */
final class RuleSchemaSelect {

  private static final String TYPE = "type";
  private static final String STRING = "string";
  private static final String ENUM = "enum";
  private static final String PROPERTIES = "properties";
  private static final String RULE_PREFIX = "ward:";
  private static final String ADDITIONAL = "additionalProperties";

  private RuleSchemaSelect() {}

  /**
   * Returns the schema of {@code [select]}; the rule names and categories come from the
   * definitions.
   */
  static Map<String, Object> select(final List<RuleDefinition> definitions) {
    final Map<String, Object> properties = obj();
    properties.put("preset", obj(TYPE, STRING, ENUM, presetKeys()));
    properties.put("languages", array(obj(TYPE, STRING, ENUM, languageKeys())));
    properties.put("categories", array(obj(TYPE, STRING, ENUM, categories(definitions))));
    properties.put("include", array(obj(TYPE, STRING, ENUM, ids(definitions))));
    properties.put("exclude", array(obj(TYPE, STRING, ENUM, ids(definitions))));
    return obj(TYPE, "object", ADDITIONAL, false, PROPERTIES, properties);
  }

  private static List<String> presetKeys() {
    return Stream.of(RulePreset.values()).map(RulePreset::key).toList();
  }

  private static List<String> languageKeys() {
    return Stream.of(AnalysisLanguage.values())
        .map(language -> language.name().toLowerCase(Locale.ROOT))
        .toList();
  }

  private static List<String> categories(final List<RuleDefinition> definitions) {
    final Set<String> categories = new TreeSet<>();
    definitions.stream()
        .map(RuleDefinition::category)
        .filter(category -> !category.isEmpty())
        .forEach(categories::add);
    return List.copyOf(categories);
  }

  private static List<String> ids(final List<RuleDefinition> definitions) {
    return definitions.stream()
        .map(definition -> definition.id().substring(RULE_PREFIX.length()).replace('/', '.'))
        .distinct()
        .sorted()
        .toList();
  }
}
