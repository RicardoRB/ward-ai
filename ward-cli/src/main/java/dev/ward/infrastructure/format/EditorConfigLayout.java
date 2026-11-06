package dev.ward.infrastructure.format;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.domain.format.FormatDefaults;
import dev.ward.domain.format.FormatLayout;
import dev.ward.domain.format.FormatLayout.ByteOrderMark;
import dev.ward.domain.format.FormatLayout.IndentStyle;
import dev.ward.domain.format.FormatLayout.LineEnding;
import dev.ward.infrastructure.format.EditorConfigValues.Setting;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Maps the standard EditorConfig keys that Ward honors to a {@link FormatLayout}. */
final class EditorConfigLayout {

  /** The standard keys Ward reads. */
  static final Set<String> KEYS =
      Set.of(
          "indent_style",
          "indent_size",
          "tab_width",
          "end_of_line",
          "insert_final_newline",
          "max_line_length",
          "charset");

  private static final String INDENT_STYLE = "indent_style";
  private static final String INDENT_SIZE = "indent_size";
  private static final String TAB_WIDTH = "tab_width";
  private static final String END_OF_LINE = "end_of_line";
  private static final String INSERT_FINAL_NEWLINE = "insert_final_newline";
  private static final String MAX_LINE_LENGTH = "max_line_length";
  private static final String CHARSET = "charset";
  private static final String TAB = "tab";
  private static final String OFF = "off";
  private static final int MINIMUM = 1;

  private EditorConfigLayout() {}

  /**
   * Builds the layout of one file from its merged standard values.
   *
   * @param effective the merged values by key
   * @param language the language of the file, for its default indent
   * @return the layout
   */
  static FormatLayout layout(
      final Map<String, Setting> effective, final AnalysisLanguage language) {
    final int defaultIndent = FormatDefaults.indentSize(language);
    final Setting indent = effective.get(INDENT_SIZE);
    final boolean tabIndent = indent != null && TAB.equals(indent.value());
    final int numericIndent =
        indent == null || tabIndent
            ? defaultIndent
            : EditorConfigValues.whole(indent, INDENT_SIZE, MINIMUM);
    final int tabWidth = EditorConfigValues.numberOr(effective, TAB_WIDTH, MINIMUM, numericIndent);
    return new FormatLayout(
        maxLineLength(effective.get(MAX_LINE_LENGTH)),
        indentStyle(effective.get(INDENT_STYLE)),
        tabIndent ? tabWidth : numericIndent,
        tabWidth,
        lineEnding(effective.get(END_OF_LINE)),
        EditorConfigValues.flag(effective, INSERT_FINAL_NEWLINE, true),
        byteOrderMark(effective.get(CHARSET)));
  }

  private static OptionalInt maxLineLength(final Setting setting) {
    if (setting == null) {
      return OptionalInt.of(FormatDefaults.LINE_WIDTH);
    }
    if (OFF.equals(setting.value())) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(EditorConfigValues.whole(setting, MAX_LINE_LENGTH, MINIMUM));
  }

  private static IndentStyle indentStyle(final Setting setting) {
    if (setting == null) {
      return IndentStyle.SPACES;
    }
    return switch (setting.value()) {
      case "space" -> IndentStyle.SPACES;
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
