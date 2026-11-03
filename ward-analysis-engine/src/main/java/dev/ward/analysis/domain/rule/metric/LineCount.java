package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/**
 * Counts the lines a node spans, from its first line to its last. A node that ends with the end of
 * input, such as a file, does not count the empty line after its final newline.
 */
final class LineCount implements Measure {

  private static final String END_OF_INPUT = "EOF";

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
    final int last = endsAtStartOfLine(node.children()) ? span.endLine() - 1 : span.endLine();
    return last - span.startLine() + 1;
  }

  /** Returns whether the last child is the end-of-input token that starts a new line. */
  private static boolean endsAtStartOfLine(final List<SyntaxNode> children) {
    if (children.isEmpty()) {
      return false;
    }
    final SyntaxNode last = children.get(children.size() - 1);
    return SyntaxTrees.isToken(last)
        && END_OF_INPUT.equals(SyntaxTrees.tokenKind(last))
        && last.span().startColumn() == 1
        && last.span().endLine() > 1;
  }
}
