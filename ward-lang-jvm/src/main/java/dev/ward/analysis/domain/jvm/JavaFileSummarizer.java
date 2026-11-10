package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.FileSummarizer;

/**
 * Summarizes a Java file from its syntax tree alone: its package, the types it declares, its
 * imports and the type names it uses. The resolver turns these into edges between files.
 */
public final class JavaFileSummarizer implements FileSummarizer {

  @Override
  public FileSummary summarize(final SourceUnit unit, final SyntaxNode tree) {
    return new JavaFileScan(unit.path()).scan(tree);
  }
}
