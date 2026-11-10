package dev.ward.analysis.domain.format;

import java.util.Objects;

/**
 * One language's formatter paired with the parser that {@link FormatSafety} uses to check its
 * output.
 *
 * @param formatter the formatter
 * @param syntax the parser for the formatter's language
 */
public record FormatterEntry(SourceFormatter formatter, FormatSyntax syntax) {
  /**
   * Checks the components.
   *
   * @throws NullPointerException if a component is missing
   */
  public FormatterEntry {
    Objects.requireNonNull(formatter, "formatter");
    Objects.requireNonNull(syntax, "syntax");
  }
}
