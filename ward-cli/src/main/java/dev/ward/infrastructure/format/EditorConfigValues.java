package dev.ward.infrastructure.format;

import dev.ward.domain.format.FormatConfigurationException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the typed values of merged EditorConfig settings and builds errors that name their origin.
 */
final class EditorConfigValues {

  private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");

  private EditorConfigValues() {}

  /** A value that a section set, kept with its origin for error messages. */
  record Setting(String value, String path, String section) {

    /**
     * Builds the error for this value.
     *
     * @param key the option key
     * @param reason why the value is rejected
     * @return the exception to throw
     */
    FormatConfigurationException invalid(final String key, final String reason) {
      return EditorConfigValues.invalid(path, section, key, reason);
    }
  }

  /**
   * Builds the error for a value that a section sets, naming the file, section and key.
   *
   * @param path the {@code .editorconfig} path, relative to the project root
   * @param section the section header
   * @param key the option key
   * @param reason why the value is rejected
   * @return the exception to throw
   */
  static FormatConfigurationException invalid(
      final String path, final String section, final String key, final String reason) {
    return new FormatConfigurationException(
        "Invalid formatting option in "
            + path
            + ", section ["
            + section
            + "], key "
            + key
            + ": "
            + reason
            + ".");
  }

  /**
   * Reads a {@code true} or {@code false} value, or returns the fallback when it is not set.
   *
   * @param effective the merged values by key
   * @param key the option key
   * @param fallback the value when the key is not set
   * @return the flag
   */
  static boolean flag(
      final Map<String, Setting> effective, final String key, final boolean fallback) {
    final Setting setting = effective.get(key);
    if (setting == null) {
      return fallback;
    }
    return switch (setting.value()) {
      case "true" -> true;
      case "false" -> false;
      default -> throw setting.invalid(key, "supported values are true, false");
    };
  }

  /**
   * Reads a whole number of at least {@code minimum}, or returns the fallback when it is not set.
   *
   * @param effective the merged values by key
   * @param key the option key
   * @param minimum the smallest accepted number
   * @param fallback the value when the key is not set
   * @return the number
   */
  static int numberOr(
      final Map<String, Setting> effective,
      final String key,
      final int minimum,
      final int fallback) {
    final Setting setting = effective.get(key);
    return setting == null ? fallback : whole(setting, key, minimum);
  }

  /**
   * Reads a whole number of at least {@code minimum} from a value that is set.
   *
   * @param setting the value
   * @param key the option key
   * @param minimum the smallest accepted number
   * @return the number
   */
  static int whole(final Setting setting, final String key, final int minimum) {
    final String value = setting.value();
    if (DIGITS.matcher(value).matches() && Integer.parseInt(value) >= minimum) {
      return Integer.parseInt(value);
    }
    throw setting.invalid(key, "expected a whole number of at least " + minimum);
  }
}
