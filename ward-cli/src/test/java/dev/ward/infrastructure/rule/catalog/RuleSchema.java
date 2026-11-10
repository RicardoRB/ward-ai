package dev.ward.infrastructure.rule.catalog;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOption;
import dev.ward.analysis.domain.rule.RulePreset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Generates the JSON Schema of {@code ward-rules.toml} from the rule definitions, so a TOML editor
 * can complete and check rule ids and options. Each shipped rule id is a property of {@code rules}
 * whose object lists the rule's options with their declared types, bounds and enum values, plus
 * {@code severity} and {@code exclude-paths}. The schema is text only: {@link RuleSchemaTest}
 * compares it with the committed file.
 */
final class RuleSchema {

  /** The JSON Schema dialect the file declares. */
  static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";

  private static final String TYPE = "type";
  private static final String STRING = "string";
  private static final String OBJECT = "object";
  private static final String ARRAY = "array";
  private static final String ITEMS = "items";
  private static final String ENUM = "enum";
  private static final String PROPERTIES = "properties";
  private static final String ADDITIONAL = "additionalProperties";
  private static final String SEVERITY = "severity";
  private static final String EXCLUDE_PATHS = "exclude-paths";
  private static final List<String> SEVERITIES = List.of("error", "warning");

  private RuleSchema() {}

  /** Returns the schema text of the given rule definitions, ending with a line break. */
  static String render(final List<RuleDefinition> definitions) {
    final StringBuilder out = new StringBuilder();
    RuleSchemaJson.write(root(definitions), 0, out);
    return out.append('\n').toString();
  }

  private static Map<String, Object> root(final List<RuleDefinition> definitions) {
    final Map<String, Object> root = obj();
    root.put("$schema", DIALECT);
    root.put("title", "ward-rules.toml");
    root.put(
        "description",
        "Rule selection, rule options, severity and excluded paths of the ward gate. Generated"
            + " from the rule catalog by RuleSchemaTest; do not edit by hand.");
    root.put(TYPE, OBJECT);
    root.put(ADDITIONAL, false);
    final Map<String, Object> properties = obj();
    properties.put("version", obj("const", 1L));
    properties.put("select", select(definitions));
    properties.put("rules", rules(definitions));
    root.put(PROPERTIES, properties);
    return root;
  }

  private static Map<String, Object> select(final List<RuleDefinition> definitions) {
    final Map<String, Object> properties = obj();
    properties.put("preset", obj(TYPE, STRING, ENUM, presetKeys()));
    properties.put("languages", array(obj(TYPE, STRING, ENUM, languageKeys())));
    properties.put("categories", array(obj(TYPE, STRING, ENUM, categories(definitions))));
    properties.put("include", array(obj(TYPE, STRING, ENUM, ids(definitions))));
    properties.put("exclude", array(obj(TYPE, STRING, ENUM, ids(definitions))));
    return obj(TYPE, OBJECT, ADDITIONAL, false, PROPERTIES, properties);
  }

  private static Map<String, Object> rules(final List<RuleDefinition> definitions) {
    final Map<String, Object> properties = obj();
    final Map<String, RuleDefinition> byId = new TreeMap<>();
    definitions.forEach(definition -> byId.put(definition.id(), definition));
    byId.forEach((id, definition) -> properties.put(id, rule(definition)));
    return obj(TYPE, OBJECT, ADDITIONAL, false, PROPERTIES, properties);
  }

  private static Map<String, Object> rule(final RuleDefinition definition) {
    final Map<String, Object> properties = obj();
    properties.put(SEVERITY, obj(TYPE, STRING, ENUM, SEVERITIES));
    properties.put(EXCLUDE_PATHS, array(obj(TYPE, STRING)));
    for (final RuleOption option : definition.options()) {
      properties.put(option.name(), option(option));
    }
    return obj(TYPE, OBJECT, ADDITIONAL, false, PROPERTIES, properties);
  }

  private static Map<String, Object> option(final RuleOption option) {
    return switch (option.type()) {
      case INTEGER -> integer(option);
      case BOOLEAN -> obj(TYPE, "boolean");
      case STRING_LIST -> array(obj(TYPE, STRING));
      case ENUM -> obj(TYPE, STRING, ENUM, option.values());
      case REGEX -> obj(TYPE, STRING, "description", "A regular expression.");
      default -> obj(TYPE, STRING);
    };
  }

  private static Map<String, Object> integer(final RuleOption option) {
    final Map<String, Object> schema = obj(TYPE, "integer");
    if (option.min() != Long.MIN_VALUE) {
      schema.put("minimum", option.min());
    }
    if (option.max() != Long.MAX_VALUE) {
      schema.put("maximum", option.max());
    }
    return schema;
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
    return definitions.stream().map(RuleDefinition::id).distinct().sorted().toList();
  }

  private static Map<String, Object> array(final Object items) {
    return obj(TYPE, ARRAY, ITEMS, items);
  }

  /** Returns a mutable object whose keys keep their insertion order. */
  private static Map<String, Object> obj(final Object... keysAndValues) {
    final Map<String, Object> map = new java.util.LinkedHashMap<>();
    for (int index = 0; index < keysAndValues.length; index += 2) {
      map.put((String) keysAndValues[index], keysAndValues[index + 1]);
    }
    return map;
  }
}
