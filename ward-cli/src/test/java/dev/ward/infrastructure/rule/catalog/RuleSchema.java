package dev.ward.infrastructure.rule.catalog;

import static dev.ward.infrastructure.rule.catalog.RuleSchemaJson.obj;

import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates the JSON Schema of {@code ward-rules.toml} from the rule definitions, so a TOML editor
 * can complete and check rule ids and options. Each shipped rule is a property of its group under
 * {@code rules}, whose object lists the rule's options with their declared types, bounds and enum
 * values, plus {@code severity} and {@code exclude-paths}. The schema is text only: {@link
 * RuleSchemaTest} compares it with the committed file.
 */
final class RuleSchema {

  /** The JSON Schema dialect the file declares. */
  static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";

  private static final String TYPE = "type";
  private static final String OBJECT = "object";
  private static final String ADDITIONAL = "additionalProperties";
  private static final String PROPERTIES = "properties";
  private static final String RULE_PREFIX = "ward:";

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
    final Map<String, Object> defs = obj();
    final Map<String, Object> properties = obj();
    properties.put("version", obj("const", 1L));
    properties.put("select", RuleSchemaSelect.select(definitions));
    properties.put("rules", rules(definitions, defs));
    root.put("$defs", defs);
    root.put(PROPERTIES, properties);
    return root;
  }

  /**
   * Returns the {@code rules} object. Each rule's schema is defined once in {@code defs} and
   * referenced from its {@code [rules.<group>.<name>]} key.
   */
  private static Map<String, Object> rules(
      final List<RuleDefinition> definitions, final Map<String, Object> defs) {
    final Map<String, Object> properties = obj();
    final Map<String, Map<String, Object>> groups = new TreeMap<>();
    final Map<String, RuleDefinition> byId = new TreeMap<>();
    definitions.forEach(definition -> byId.put(definition.id(), definition));
    byId.forEach(
        (id, definition) -> {
          defs.put(id, RuleOptionSchema.rule(definition));
          groups.computeIfAbsent(group(id), key -> obj()).put(name(id), ref(id));
        });
    groups.forEach(
        (group, members) ->
            properties.put(group, obj(TYPE, OBJECT, ADDITIONAL, false, PROPERTIES, members)));
    return obj(TYPE, OBJECT, ADDITIONAL, false, PROPERTIES, properties);
  }

  private static Map<String, Object> ref(final String id) {
    return obj("$ref", "#/$defs/" + id.replace("/", "~1"));
  }

  /** Returns the group of {@code ward:<group>/<name>}, such as {@code java}. */
  private static String group(final String id) {
    return id.substring(RULE_PREFIX.length(), id.indexOf('/'));
  }

  private static String name(final String id) {
    return id.substring(id.indexOf('/') + 1);
  }
}
