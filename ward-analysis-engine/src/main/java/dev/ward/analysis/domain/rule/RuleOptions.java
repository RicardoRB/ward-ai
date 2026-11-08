package dev.ward.analysis.domain.rule;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Puts configured option values into a rule body. A value overrides the body key that its option
 * names in {@link RuleOption#parameter()}; a key that is not declared as an option cannot be
 * overridden. The body keeps its other text, so the cache key, which hashes the body, changes with
 * every value.
 *
 * <p>Two body layouts exist. A body of several lines is TOML, and the line {@code key = ...} is
 * replaced (the value stays on one line). A one-line body is {@code key=value} pairs separated by
 * {@code ;}, and a text value must not contain {@code ;}. A key that the body does not set is added
 * at the end, so a rule whose default lives in its matcher can be configured as well.
 */
public final class RuleOptions {
  private static final String PAIR_SEPARATOR = ";";

  private RuleOptions() {}

  /**
   * Returns {@code rule} with the values of {@code values}, keyed by option name, in its body.
   * Values must already satisfy {@link RuleOption#problem(Object)}.
   *
   * @throws IllegalArgumentException if a name is not a declared option, or a text value cannot be
   *     written in the body layout
   */
  public static RuleDefinition apply(final RuleDefinition rule, final Map<String, Object> values) {
    String body = rule.body();
    for (final Map.Entry<String, Object> entry : values.entrySet()) {
      final RuleOption option = declared(rule, entry.getKey());
      body = substitute(body, option.parameter(), entry.getValue());
    }
    return new RuleDefinition(
        rule.id(),
        rule.languages(),
        rule.capability(),
        rule.severity(),
        body,
        rule.category(),
        rule.presets(),
        rule.options(),
        rule.minJavaRelease());
  }

  private static RuleOption declared(final RuleDefinition rule, final String name) {
    return rule.option(name)
        .orElseThrow(
            () -> new IllegalArgumentException("unknown option '" + name + "' of " + rule.id()));
  }

  private static String substitute(final String body, final String key, final Object value) {
    return body.contains("\n") ? tomlBody(body, key, value) : pairBody(body, key, value);
  }

  private static String tomlBody(final String body, final String key, final Object value) {
    final String line = key + " = " + toml(value);
    final Matcher matcher =
        Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*=.*$").matcher(body);
    return matcher.find()
        ? matcher.replaceFirst(Matcher.quoteReplacement(line))
        : body.stripTrailing() + "\n" + line + "\n";
  }

  private static String pairBody(final String body, final String key, final Object value) {
    final String text = plain(value);
    if (text.contains(PAIR_SEPARATOR)) {
      throw new IllegalArgumentException("the value of '" + key + "' must not contain ';'");
    }
    final String pair = key + "=" + text;
    final List<String> pairs =
        body.isBlank()
            ? List.of()
            : Stream.of(body.split(PAIR_SEPARATOR)).map(String::strip).toList();
    boolean replaced = false;
    final StringBuilder result = new StringBuilder();
    for (final String existing : pairs) {
      final boolean same = existing.startsWith(key + "=");
      replaced |= same;
      append(result, same ? pair : existing);
    }
    if (!replaced) {
      append(result, pair);
    }
    return result.toString();
  }

  private static void append(final StringBuilder result, final String pair) {
    if (!result.isEmpty()) {
      result.append("; ");
    }
    result.append(pair);
  }

  private static String plain(final Object value) {
    return value instanceof List<?> list
        ? String.join(",", list.stream().map(String::valueOf).toList())
        : String.valueOf(value);
  }

  private static String toml(final Object value) {
    if (value instanceof List<?> list) {
      return "[" + String.join(", ", list.stream().map(RuleOptions::toml).toList()) + "]";
    }
    if (value instanceof String text) {
      return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
    return String.valueOf(value);
  }
}
