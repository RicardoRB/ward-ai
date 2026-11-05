package dev.ward.analysis.domain.format;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import java.util.List;
import java.util.function.Function;

/** A formatter whose behavior is a function of the source, for the safety tests. */
final class StubFormatter implements SourceFormatter {

  private final AnalysisLanguage language;
  private final Function<String, FormatResult> behavior;

  StubFormatter(final AnalysisLanguage language, final Function<String, FormatResult> behavior) {
    this.language = language;
    this.behavior = behavior;
  }

  @Override
  public AnalysisLanguage language() {
    return language;
  }

  @Override
  public List<FormatOption> options() {
    return List.of();
  }

  @Override
  public FormatResult format(final String source, final FormatOptions options) {
    return behavior.apply(source);
  }
}
