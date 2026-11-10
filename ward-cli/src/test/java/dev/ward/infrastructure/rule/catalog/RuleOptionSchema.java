package dev.ward.infrastructure.rule.catalog;

import static dev.ward.infrastructure.rule.catalog.RuleSchemaJson.array;
import static dev.ward.infrastructure.rule.catalog.RuleSchemaJson.obj;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOption;
import java.util.List;
import java.util.Map;

/**
 * Builds the schema of one rule table: {@code severity}, {@code exclude-paths} and the options the
 * rule declares, each with its declared type, bounds and enum values.
 */
final class RuleOptionSchema {

  private static final String TYPE = "type";
  private static final String STRING = "string";
  private static final String ENUM = "enum";
  private static final String PROPERTIES = "properties";
  private static final String ADDITIONAL = "additionalProperties";
  private static final List<String> SEVERITIES = List.of("error", "warning");

  private RuleOptionSchema() {}

  /** Returns the schema of the table of one rule. */
  static Map<String, Object> rule(final RuleDefinition definition) {
    final Map<String, Object> properties = obj();
    properties.put("severity", obj(TYPE, STRING, ENUM, SEVERITIES));
    properties.put("exclude-paths", array(obj(TYPE, STRING)));
    for (final RuleOption option : definition.options()) {
      properties.put(option.name(), option(option));
    }
    return obj(TYPE, "object", ADDITIONAL, false, PROPERTIES, properties);
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
}
