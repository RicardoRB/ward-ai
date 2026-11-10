package dev.ward.infrastructure.rule.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Writes the rule options of a typescript-eslint parity case as JavaScript literals. */
final class TypeScriptEslintOptions {

  private static final String DESCRIPTION_FORMAT = "description-format";
  private static final String DIRECTIVE = "ts-expect-error";
  private static final char DASH = '-';
  private static final Map<String, String> OPTION_NAMES =
      Map.of("ignoreIife", "ignoreIIFE", "allowRegexp", "allowRegExp");

  private TypeScriptEslintOptions() {}

  static String options(final Map<String, Object> values) {
    if (values.isEmpty()) {
      return "";
    }
    final List<String> entries = new ArrayList<>();
    for (final Map.Entry<String, Object> entry : values.entrySet()) {
      entries.add(option(entry.getKey(), entry.getValue()));
    }
    return ", {" + String.join(", ", entries) + "}";
  }

  /** Writes one option; a description format belongs to the directive, as in typescript-eslint. */
  private static String option(final String key, final Object value) {
    if (DESCRIPTION_FORMAT.equals(key)) {
      return json(DIRECTIVE) + ": {" + json("descriptionFormat") + ": " + json(value) + "}";
    }
    return optionKey(key) + ": " + json(value);
  }

  /** Converts a Ward option name to the name typescript-eslint reads. */
  private static String optionKey(final String key) {
    if (key.startsWith("ts-")) {
      return json(key);
    }
    final StringBuilder camel = new StringBuilder();
    boolean upper = false;
    for (final char c : key.toCharArray()) {
      if (DASH == c) {
        upper = true;
      } else {
        camel.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return OPTION_NAMES.getOrDefault(camel.toString(), camel.toString());
  }

  static String json(final Object value) {
    if (value instanceof List<?> list) {
      return list.stream()
          .map(TypeScriptEslintOptions::json)
          .collect(Collectors.joining(", ", "[", "]"));
    }
    if (value instanceof String text) {
      return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    return String.valueOf(value);
  }
}
