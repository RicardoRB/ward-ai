package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;

/** Builds the summary of one source unit from its text and syntax tree only. */
@FunctionalInterface
public interface FileSummarizer {

  /**
   * Summarizes the unit. The result must not depend on any other file.
   *
   * @param unit the source unit
   * @param tree the syntax tree of the unit
   * @return the summary of the unit
   */
  FileSummary summarize(SourceUnit unit, SyntaxNode tree);
}
