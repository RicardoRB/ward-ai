package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.FileSummary;
import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.port.FileSummarizer;
import java.util.List;

/**
 * Summarizes a Rust file by the modules it names: each {@code use} path and each {@code mod x;}
 * becomes a reference spelled as a path from {@code crate}, {@code self}, {@code super} or another
 * crate. A path inside an inline module is rewritten against the file's module, so that {@code use
 * super::*} in a {@code tests} module is a reference to the file itself.
 */
public final class RustFileSummarizer implements FileSummarizer {
  @Override
  public FileSummary summarize(final SourceUnit unit, final SyntaxNode tree) {
    return new FileSummary(unit.path(), List.of(), new RustImports(tree).references(), List.of());
  }
}
