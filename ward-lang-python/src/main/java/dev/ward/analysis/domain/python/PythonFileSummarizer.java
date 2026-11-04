package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.FileSummarizer;
import java.util.List;

/** Summarizes a Python file from its syntax tree: its imports and its exported names. */
public final class PythonFileSummarizer implements FileSummarizer {

  @Override
  public FileSummary summarize(final SourceUnit unit, final SyntaxNode tree) {
    return new FileSummary(
        unit.path(), PythonExports.of(tree), PythonImportReferences.of(tree), List.of());
  }
}
