package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;
import java.util.Set;

/**
 * Counts cyclomatic complexity: one plus the decision points inside a node. Nested scopes, such as
 * an inner function, are skipped; they are measured as nodes of their own.
 */
final class DecisionCount implements Measure {

  private static final int BASE = 1;
  private static final String TEXT_SEPARATOR = "#";

  @Override
  public Optional<Violation> check(
      final SyntaxNode node,
      final MetricRule.Target target,
      final boolean method,
      final MetricRule rule) {
    final int complexity = BASE + decisions(node, rule);
    if (complexity <= rule.max()) {
      return Optional.empty();
    }
    return Optional.of(
        new Violation(node.span(), SyntaxTrees.nameOf(node, rule.keys().name()), complexity, ""));
  }

  private static int decisions(final SyntaxNode node, final MetricRule rule) {
    int count = 0;
    for (final SyntaxNode child : node.children()) {
      if (!rule.keys().nested().contains(child.kind())) {
        count += counts(node, child, rule.keys().decisions()) + decisions(child, rule);
      }
    }
    return count;
  }

  /**
   * Returns 1 when {@code child} is a decision point, given its parent, otherwise 0. A decision is
   * a kind, a {@code parent>token} pair, or a {@code kind#text} pair that matches the child's text.
   */
  private static int counts(
      final SyntaxNode parent, final SyntaxNode child, final Set<String> decisions) {
    if (decisions.contains(child.kind())
        || decisions.contains(child.kind() + TEXT_SEPARATOR + SyntaxTrees.text(child))) {
      return 1;
    }
    final boolean token = SyntaxTrees.isToken(child);
    return token && decisions.contains(parent.kind() + ">" + SyntaxTrees.tokenKind(child)) ? 1 : 0;
  }
}
