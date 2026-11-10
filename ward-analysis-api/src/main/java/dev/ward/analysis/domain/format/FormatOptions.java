package dev.ward.analysis.domain.format;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The resolved option values for one formatter run, keyed by option name. Immutable.
 *
 * @param values the values, copied on construction
 */
public record FormatOptions(Map<String, String> values) {

  /**
   * Copies the values.
   *
   * @throws NullPointerException if the map is missing
   */
  public FormatOptions {
    values = Map.copyOf(new LinkedHashMap<>(values));
  }

  /**
   * Builds the options that take every declared default.
   *
   * @param declared the options a formatter declares
   * @return the default values
   */
  public static FormatOptions defaults(final List<FormatOption> declared) {
    final Map<String, String> defaults = new LinkedHashMap<>();
    for (final FormatOption option : declared) {
      defaults.put(option.key(), option.defaultValue());
    }
    return new FormatOptions(defaults);
  }

  /**
   * Returns one option value.
   *
   * @param key the option name
   * @return the value
   * @throws IllegalArgumentException if no value is set for the key
   */
  public String get(final String key) {
    final String value = values.get(key);
    if (value == null) {
      throw new IllegalArgumentException("no formatting option " + key);
    }
    return value;
  }

  /**
   * Returns one option value as a whole number.
   *
   * @param key the option name
   * @return the value
   * @throws NumberFormatException if the value is not a whole number
   */
  public int integer(final String key) {
    return Integer.parseInt(get(key));
  }
}
