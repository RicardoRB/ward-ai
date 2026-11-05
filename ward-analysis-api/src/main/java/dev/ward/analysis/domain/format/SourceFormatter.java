package dev.ward.analysis.domain.format;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;

/**
 * Rewrites the whitespace of one language's source. A formatter changes only line breaks,
 * indentation and spaces, and never a token; {@code FormatSafety} in the engine checks that before
 * any result is written.
 */
public interface SourceFormatter {

  /**
   * Returns the language this formatter handles.
   *
   * @return the language
   */
  AnalysisLanguage language();

  /**
   * Declares the options this formatter reads from its language table.
   *
   * @return the options, in declaration order
   */
  List<FormatOption> options();

  /**
   * Formats one source text.
   *
   * @param source the source text, with its line endings normalized to {@code \n}
   * @param options the option values; every declared option is present
   * @return the formatted text, {@link FormatResult.Unchanged} when it is already formatted, or
   *     {@link FormatResult.Refused} when the formatter cannot format it
   */
  FormatResult format(String source, FormatOptions options);
}
