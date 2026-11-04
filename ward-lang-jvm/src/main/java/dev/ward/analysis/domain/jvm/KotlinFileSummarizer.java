package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.FileSummarizer;
import java.util.List;

/**
 * Summarizes a Kotlin file from its syntax tree alone, in the vocabulary of the Java summaries, so
 * one resolver indexes both languages. Build scripts are not compiled into a module, so a {@code
 * .kts} file has an empty summary.
 */
public final class KotlinFileSummarizer implements FileSummarizer {
  private static final String SCRIPT_SUFFIX = ".kts";

  @Override
  public FileSummary summarize(final SourceUnit unit, final SyntaxNode tree) {
    if (unit.path().endsWith(SCRIPT_SUFFIX)) {
      return new FileSummary(unit.path(), List.of(), List.of(), List.of());
    }
    return new KotlinFileScan(unit.path()).scan(tree);
  }
}
