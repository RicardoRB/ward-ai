package dev.ward.analysis.domain.format;

import java.util.List;
import java.util.Objects;

/**
 * One option a formatter declares, by plain kebab-case key, for its language.
 *
 * @param key the plain kebab-case option name
 * @param type the value type
 * @param defaultValue the value used when the file does not set the option
 * @param allowedValues the only accepted values, or an empty list when any value of the type is
 *     accepted; copied on construction
 */
public record FormatOption(
    String key, FormatOptionType type, String defaultValue, List<String> allowedValues) {

  /**
   * Copies the allowed values and checks that the default value is itself accepted.
   *
   * @throws NullPointerException if a component is missing
   * @throws IllegalArgumentException if the default value is not accepted by this option
   */
  public FormatOption {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(defaultValue, "defaultValue");
    allowedValues = List.copyOf(allowedValues);
    if (!accepts(type, allowedValues, defaultValue)) {
      throw new IllegalArgumentException("default value of " + key + " is not accepted");
    }
  }

  /**
   * Tells whether a value has this option's type and, when restricted, is an allowed value.
   *
   * @param value the candidate value
   * @return true when the value is accepted
   */
  public boolean accepts(final String value) {
    Objects.requireNonNull(value, "value");
    return accepts(type, allowedValues, value);
  }

  private static boolean accepts(
      final FormatOptionType type, final List<String> allowedValues, final String value) {
    if (!hasType(type, value)) {
      return false;
    }
    return allowedValues.isEmpty() || allowedValues.contains(value);
  }

  private static boolean hasType(final FormatOptionType type, final String value) {
    return switch (type) {
      case INTEGER -> isInteger(value);
      case BOOLEAN -> "true".equals(value) || "false".equals(value);
      case TEXT -> true;
    };
  }

  private static boolean isInteger(final String value) {
    try {
      Integer.parseInt(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
