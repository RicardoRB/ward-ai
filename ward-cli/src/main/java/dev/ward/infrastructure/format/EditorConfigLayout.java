package dev.ward.infrastructure.format;

import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.domain.format.FormatLayout;
import dev.ward.domain.format.FormatLayout.ByteOrderMark;
import dev.ward.domain.format.FormatLayout.IndentStyle;
import dev.ward.domain.format.FormatLayout.LineEnding;
import dev.ward.infrastructure.format.EditorConfigValues.Setting;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Maps the standard EditorConfig keys that Ward honors to a {@link FormatLayout}. Where a key is
 * unset, the layout takes the value that the formatter declares as its default.
 */
final class EditorConfigLayout {

  /** The shared formatter option for the line width. */
  static final String LINE_WIDTH_OPTION = "line-width";

  /** The shared formatter option for the indent style, {@code space} or {@code tab}. */
  static final String INDENT_STYLE_OPTION = "indent-style";

  /** The shared formatter option for the indent width. */
  static final String INDENT_WIDTH_OPTION = "indent-width";

  /** The shared formatter option for the most blank lines kept in a run. */
  static final String MAX_BLANK_LINES_OPTION = "max-blank-lines";

  static final String INDENT_STYLE = "indent_style";
  static final String INDENT_SIZE = "indent_size";
  static final String TAB_WIDTH = "tab_width";
  static final String END_OF_LINE = "end_of_line";
  static final String INSERT_FINAL_NEWLINE = "insert_final_newline";
  static final String MAX_LINE_LENGTH = "max_line_length";
  static final String CHARSET = "charset";

  /** The standard keys Ward reads. */
  static final Set<String> KEYS =
      Set.of(
          INDENT_STYLE,
          INDENT_SIZE,
          TAB_WIDTH,
          END_OF_LINE,
          INSERT_FINAL_NEWLINE,
          MAX_LINE_LENGTH,
          CHARSET);

  /**
   * Used only when a formatter does not declare the shared option: every formatter declares them.
   */
  static final String FALLBACK_LINE_WIDTH = "100";

  static final String FALLBACK_INDENT_WIDTH = "4";
  static final String FALLBACK_INDENT_STYLE = "space";
  static final String FALLBACK_MAX_BLANK_LINES = "1";

  private static final String TAB = "tab";
  private static final String SPACE = "space";
  private static final String OFF = "off";
  private static final int MINIMUM = 1;

  private EditorConfigLayout() {}

  /**
   * Builds the layout of one file from its merged standard values.
   *
   * @param effective the merged values by key
   * @param defaults the formatter's declared defaults; an undeclared shared option uses a fallback
   * @return the layout
   */
  static FormatLayout layout(final Map<String, Setting> effective, final FormatOptions defaults) {
    final int defaultIndent = declaredInt(defaults, INDENT_WIDTH_OPTION, FALLBACK_INDENT_WIDTH);
    final Setting indent = effective.get(INDENT_SIZE);
    final boolean tabIndent = indent != null && TAB.equals(indent.value());
    final int numericIndent =
        indent == null || tabIndent
            ? defaultIndent
            : EditorConfigValues.whole(indent, INDENT_SIZE, MINIMUM);
    final int tabWidth = EditorConfigValues.numberOr(effective, TAB_WIDTH, MINIMUM, numericIndent);
    return new FormatLayout(
        maxLineLength(
            effective.get(MAX_LINE_LENGTH),
            declaredInt(defaults, LINE_WIDTH_OPTION, FALLBACK_LINE_WIDTH)),
        indentStyle(
            effective.get(INDENT_STYLE),
            defaults.values().getOrDefault(INDENT_STYLE_OPTION, FALLBACK_INDENT_STYLE)),
        tabIndent ? tabWidth : numericIndent,
        tabWidth,
        lineEnding(effective.get(END_OF_LINE)),
        EditorConfigValues.flag(effective, INSERT_FINAL_NEWLINE, true),
        byteOrderMark(effective.get(CHARSET)));
  }

  /**
   * Reads a declared whole-number default.
   *
   * @param defaults the declared defaults
   * @param key the shared option key
   * @param fallback the value used when the formatter does not declare the key
   * @return the default number
   */
  static int declaredInt(final FormatOptions defaults, final String key, final String fallback) {
    return Integer.parseInt(defaults.values().getOrDefault(key, fallback));
  }

  private static OptionalInt maxLineLength(final Setting setting, final int fallback) {
    if (setting == null) {
      return OptionalInt.of(fallback);
    }
    if (OFF.equals(setting.value())) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(EditorConfigValues.whole(setting, MAX_LINE_LENGTH, MINIMUM));
  }

  private static IndentStyle indentStyle(final Setting setting, final String fallback) {
    if (setting == null) {
      return TAB.equals(fallback) ? IndentStyle.TABS : IndentStyle.SPACES;
    }
    return switch (setting.value()) {
      case SPACE -> IndentStyle.SPACES;
      case TAB -> IndentStyle.TABS;
      default -> throw setting.invalid(INDENT_STYLE, "supported values are space, tab");
    };
  }

  private static Optional<LineEnding> lineEnding(final Setting setting) {
    if (setting == null) {
      return Optional.empty();
    }
    return switch (setting.value()) {
      case "lf" -> Optional.of(LineEnding.LF);
      case "crlf" -> Optional.of(LineEnding.CRLF);
      default -> throw setting.invalid(END_OF_LINE, "supported values are lf, crlf");
    };
  }

  private static ByteOrderMark byteOrderMark(final Setting setting) {
    if (setting == null) {
      return ByteOrderMark.PRESERVE;
    }
    return switch (setting.value()) {
      case "utf-8-bom" -> ByteOrderMark.REQUIRED;
      case "utf-8" -> ByteOrderMark.PRESERVE;
      default -> throw setting.invalid(CHARSET, "supported values are utf-8, utf-8-bom");
    };
  }
}
