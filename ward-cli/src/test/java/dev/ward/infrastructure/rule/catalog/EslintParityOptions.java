package dev.ward.infrastructure.rule.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Writes a rule's option table from a fixture case as an ESLint option literal. */
final class EslintParityOptions {
  private static final Set<String> ACRONYMS = Set.of("jsx");

  /** The options that ESLint takes as positional arguments, before its option object. */
  private static final Set<String> POSITIONAL = Set.of("declarations");

  private EslintParityOptions() {}

  /** The option arguments after the severity: positional values first, then the option object. */
  static String arguments(final Map<String, Object> values) {
    final StringBuilder out = new StringBuilder();
    final Map<String, Object> object = new LinkedHashMap<>();
    for (final Map.Entry<String, Object> entry : values.entrySet()) {
      if (POSITIONAL.contains(entry.getKey())) {
        out.append(", ").append(scalar(entry.getValue()));
      } else {
        object.put(entry.getKey(), entry.getValue());
      }
    }
    return object.isEmpty() ? out.toString() : out + ", " + jsObject(object);
  }

  /** A JavaScript object literal of an option table, with its keys in camel case. */
  static String jsObject(final Map<String, Object> values) {
    final List<String> entries = new ArrayList<>();
    for (final Map.Entry<String, Object> entry : values.entrySet()) {
      entries.add(camel(entry.getKey()) + ": " + jsValue(entry.getValue()));
    }
    return "{ " + String.join(", ", entries) + " }";
  }

  private static String camel(final String kebab) {
    final StringBuilder camel = new StringBuilder();
    for (final String part : kebab.split("-")) {
      camel.append(camel.isEmpty() ? part : capitalized(part));
    }
    return camel.toString();
  }

  /** A word after the first, as ESLint writes it: an acronym such as JSX stays upper case. */
  private static String capitalized(final String part) {
    return ACRONYMS.contains(part)
        ? part.toUpperCase(Locale.ROOT)
        : part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1);
  }

  private static String jsValue(final Object value) {
    if (value instanceof List<?> list) {
      return "["
          + list.stream().map(EslintParityOptions::scalar).collect(Collectors.joining(", "))
          + "]";
    }
    return scalar(value);
  }

  /** A boolean or number as written, anything else as a quoted string. */
  private static String scalar(final Object value) {
    if (value instanceof Boolean || value instanceof Number) {
      return value.toString();
    }
    return "\"" + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
