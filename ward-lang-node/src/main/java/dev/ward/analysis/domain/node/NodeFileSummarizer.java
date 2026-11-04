package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.FileSummarizer;

/**
 * Summarizes a TypeScript or JavaScript file from its syntax tree alone: its module references
 * (static, type-only, {@code require}, dynamic literal {@code import()}) and its exported names.
 */
public final class NodeFileSummarizer implements FileSummarizer {

  @Override
  public FileSummary summarize(final SourceUnit unit, final SyntaxNode tree) {
    final NodeModuleScan scan = new NodeModuleScan(tree);
    scan.run();
    return new FileSummary(unit.path(), scan.exports(), scan.references(), scan.declared());
  }
}
