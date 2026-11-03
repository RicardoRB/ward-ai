package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;

/**
 * Counts the lines a node spans, from its first line to its last. The span of a node that ends with
 * the end of input already stops at its last real line, so the empty line after a final newline is
 * not counted.
 */
final class LineCount implements Measure {

  @Override
  public Optional<Violation> check(
      final SyntaxNode node,
      final MetricRule.Target target,
      final boolean method,
      final MetricRule rule) {
    final int lines = lines(node);
    if (lines <= rule.max()) {
      return Optional.empty();
    }
    return Optional.of(
        new Violation(node.span(), SyntaxTrees.nameOf(node, rule.keys().name()), lines, ""));
  }

  private static int lines(final SyntaxNode node) {
    final Span span = node.span();
    return span.endLine() - span.startLine() + 1;
  }
}
