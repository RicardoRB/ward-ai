package dev.ward.domain.format;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The file layout a formatter must produce: line width, indentation, line endings and the final
 * newline and byte-order mark.
 *
 * @param maxLineLength the line width, or empty when lines are not wrapped
 * @param indentStyle whether indentation uses spaces or tabs
 * @param indentSize the indentation of one level, in columns or tabs
 * @param tabWidth the width of one tab in columns
 * @param lineEnding the line ending, or empty to keep each file's own
 * @param insertFinalNewline whether the file ends with a line break
 * @param byteOrderMark whether the file must start with a byte-order mark
 */
public record FormatLayout(
    OptionalInt maxLineLength,
    IndentStyle indentStyle,
    int indentSize,
    int tabWidth,
    Optional<LineEnding> lineEnding,
    boolean insertFinalNewline,
    ByteOrderMark byteOrderMark) {

  /**
   * Checks the components.
   *
   * @throws NullPointerException if a component is missing
   */
  public FormatLayout {
    Objects.requireNonNull(maxLineLength, "maxLineLength");
    Objects.requireNonNull(indentStyle, "indentStyle");
    Objects.requireNonNull(lineEnding, "lineEnding");
    Objects.requireNonNull(byteOrderMark, "byteOrderMark");
  }

  /** The character that indents one level. */
  public enum IndentStyle {
    /** Spaces. */
    SPACES,
    /** Tabs. */
    TABS
  }

  /** The line ending of a file. */
  public enum LineEnding {
    /** A line feed. */
    LF,
    /** A carriage return and a line feed. */
    CRLF
  }

  /** Whether the file starts with a byte-order mark. */
  public enum ByteOrderMark {
    /** Keep the mark the file already has. */
    PRESERVE,
    /** The file must start with a UTF-8 byte-order mark. */
    REQUIRED
  }
}
