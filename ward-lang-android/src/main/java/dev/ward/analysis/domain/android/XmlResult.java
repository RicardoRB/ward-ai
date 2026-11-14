package dev.ward.analysis.domain.android;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * What {@link XmlParser} reads from a text: the tree, which is an empty document when the text is
 * not well formed, and the problem found.
 *
 * @param tree the root node
 * @param diagnostics at most one problem; empty when the text is well formed
 */
public record XmlResult(SyntaxNode tree, List<XmlDiagnostic> diagnostics) {

  /** Copies the diagnostics. */
  public XmlResult {
    diagnostics = List.copyOf(diagnostics);
  }
}
