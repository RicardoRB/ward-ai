package dev.ward.infrastructure.format;

import dev.ward.analysis.domain.format.FormatOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps a formatter's declared options to the EditorConfig keys that feed them, and the values they
 * take. The shared options map to standard keys; language options map to {@code ward_} keys.
 */
final class EditorConfigOptions {

  private static final String TAB = "tab";
  private static final String OFF = "off";
  private static final Set<String> SHARED_OPTIONS =
      Set.of(
          EditorConfigLayout.LINE_WIDTH_OPTION,
          EditorConfigLayout.INDENT_STYLE_OPTION,
          EditorConfigLayout.INDENT_WIDTH_OPTION,
          EditorConfigLayout.MAX_BLANK_LINES_OPTION);

  private EditorConfigOptions() {}

  /**
   * Tells whether an option is one of the four shared options.
   *
   * @param optionKey the plain kebab-case option key
   * @return true for the shared options
   */
  static boolean isShared(final String optionKey) {
    return SHARED_OPTIONS.contains(optionKey);
  }

  /**
   * Returns the EditorConfig key that feeds a declared option. {@code indent-width} reads {@code
   * tab_width} when {@code indent_size = tab}.
   *
   * @param optionKey the plain kebab-case option key
   * @param effective the merged values by key
   * @return the EditorConfig key
   */
  static String sourceKey(
      final String optionKey, final Map<String, EditorConfigValues.Setting> effective) {
    return switch (optionKey) {
      case EditorConfigLayout.LINE_WIDTH_OPTION -> EditorConfigLayout.MAX_LINE_LENGTH;
      case EditorConfigLayout.INDENT_STYLE_OPTION -> EditorConfigLayout.INDENT_STYLE;
      case EditorConfigLayout.INDENT_WIDTH_OPTION ->
          usesTabWidth(effective) ? EditorConfigLayout.TAB_WIDTH : EditorConfigLayout.INDENT_SIZE;
      case EditorConfigLayout.MAX_BLANK_LINES_OPTION -> EditorConfigSettings.WARD_MAX_BLANK_LINES;
      default -> EditorConfigSettings.WARD_PREFIX + optionKey.replace('-', '_');
    };
  }

  /**
   * Returns the option value for a configured value. {@code off} for the line width becomes {@code
   * Integer.MAX_VALUE} in the options only.
   *
   * @param option the declared option
   * @param value the configured value
   * @return the value the formatter receives
   */
  static String optionValue(final FormatOption option, final String value) {
    final boolean unbounded = EditorConfigLayout.LINE_WIDTH_OPTION.equals(option.key());
    return unbounded && OFF.equals(value) ? String.valueOf(Integer.MAX_VALUE) : value;
  }

  /**
   * Describes the values an option accepts, for an error message.
   *
   * @param option the declared option
   * @return the reason text
   */
  static String optionReason(final FormatOption option) {
    if (option.allowedValues().isEmpty()) {
      return "expected a " + option.type().name().toLowerCase(Locale.ROOT) + " value";
    }
    return "supported values are "
        + option.allowedValues().stream().collect(Collectors.joining(", "));
  }

  /** {@code indent_size = tab} means the indent width is {@code tab_width}. */
  private static boolean usesTabWidth(final Map<String, EditorConfigValues.Setting> effective) {
    final EditorConfigValues.Setting indent = effective.get(EditorConfigLayout.INDENT_SIZE);
    return indent != null && TAB.equals(indent.value());
  }
}
