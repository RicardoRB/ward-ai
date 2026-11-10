package dev.ward.infrastructure.rule;

import dev.ward.analysis.application.rule.TomlValues;
import java.util.List;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

/** Lists the user options of a generic check with the values its rule body sets. */
final class CheckOptions {

  private CheckOptions() {}

  /**
   * Lists the user options of the generic check that {@code body} selects, with the values the body
   * sets; a legacy check has none.
   *
   * @throws dev.ward.analysis.domain.rule.RuleConfigurationException if the body is not valid TOML
   *     or has no {@code check}
   */
  static List<CheckOption> of(final String body) {
    final TomlParseResult toml = TomlValues.parse(body);
    return GenericKind.select(TomlValues.required(toml, "check"), toml)
        .map(kind -> of(kind, toml))
        .orElse(List.of());
  }

  /** Lists the user options of the body's kind with the values the body sets. */
  static List<CheckOption> of(final GenericKind kind, final TomlParseResult toml) {
    return kind.options().stream()
        .map(option -> new CheckOption(option.name(), option.type(), valueOf(toml, option.name())))
        .toList();
  }

  private static String valueOf(final TomlParseResult toml, final String key) {
    final Object value = toml.get(key);
    if (value instanceof TomlArray array) {
      return String.join(",", array.toList().stream().map(String::valueOf).toList());
    }
    return value == null ? "" : String.valueOf(value);
  }
}
