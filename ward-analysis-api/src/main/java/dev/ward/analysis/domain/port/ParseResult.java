package dev.ward.analysis.domain.port;

import dev.ward.analysis.domain.ir.AnalysisFinding;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of parsing one unit: a syntax tree, possibly partial, and the diagnostics for the
 * syntax errors found while building it.
 *
 * @param tree the root node of the syntax tree
 * @param diagnostics one finding per syntax error, copied on construction
 */
public record ParseResult(SyntaxNode tree, List<AnalysisFinding> diagnostics) {

  /**
   * Copies the diagnostics.
   *
   * @throws NullPointerException if the tree or the diagnostics list is missing
   */
  public ParseResult {
    Objects.requireNonNull(tree, "tree");
    diagnostics = List.copyOf(diagnostics);
  }
}
