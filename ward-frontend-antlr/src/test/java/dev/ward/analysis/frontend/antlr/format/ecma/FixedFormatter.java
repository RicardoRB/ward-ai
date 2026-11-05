package dev.ward.analysis.frontend.antlr.format.ecma;

import dev.ward.analysis.domain.format.FormatOption;
import dev.ward.analysis.domain.format.FormatOptions;
import dev.ward.analysis.domain.format.FormatResult;
import dev.ward.analysis.domain.format.SourceFormatter;
import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;

/** A formatter that returns one fixed text, to test what the safety checks do with it. */
final class FixedFormatter implements SourceFormatter {

  private final SourceFormatter delegate;
  private final String output;

  FixedFormatter(final SourceFormatter delegate, final String output) {
    this.delegate = delegate;
    this.output = output;
  }

  @Override
  public AnalysisLanguage language() {
    return delegate.language();
  }

  @Override
  public List<FormatOption> options() {
    return delegate.options();
  }

  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    return new FormatResult.Formatted(output);
  }
}
