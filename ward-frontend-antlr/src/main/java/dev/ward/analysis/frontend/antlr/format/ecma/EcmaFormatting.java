package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.DocPrinter;
import dev.ward.analysis.domain.format.Document;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;

/** Lays out one parsed TypeScript or JavaScript source with the shared document builder. */
final class EcmaFormatting {

  private EcmaFormatting() {}

  /**
   * Formats a parsed source, refusing it when it has a syntax error.
   *
   * @param source the source text
   * @param options the option values
   * @param parse the parse of the source
   * @return the formatted text, {@code Unchanged} or {@code Refused}
   */
  static FormatResult format(
      final String source, final FormatOptions options, final EcmaParse parse) {
    if (parse.errorLine().isPresent()) {
      return new FormatResult.Refused("input has a syntax error", parse.errorLine().getAsInt());
    }
    final Document document =
        new EcmaDocBuilder(parse.tokens(), options.integer(EcmaOptions.MAX_BLANK_LINES)).build();
    final String printed = new DocPrinter(EcmaOptions.printStyle(options)).print(document);
    return printed.equals(source)
        ? new FormatResult.Unchanged()
        : new FormatResult.Formatted(printed);
  }
}
