package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.SourceFormatter;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;

/** Formats TypeScript in Ward style: whitespace and line breaks only, never a token. */
public final class TypeScriptFormatter implements SourceFormatter {

  @Override
  public AnalysisLanguage language() {
    return AnalysisLanguage.TYPESCRIPT;
  }

  @Override
  public List<FormatOption> options() {
    return EcmaOptions.DECLARED;
  }

  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    return EcmaFormatting.format(source, options, EcmaParsing.typeScript(source));
  }
}
