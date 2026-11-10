package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Optional;

/** Takes one measurement of a node and reports a violation when it breaks the rule. */
@FunctionalInterface
interface Measure {

  /**
   * Returns a violation when {@code node} breaks the rule.
   *
   * @param node a node whose kind is a target of the rule
   * @param target the target that matched the node
   * @param method whether the node is a function declared directly in a class body
   * @param rule the rule being checked
   */
  Optional<Violation> check(
      SyntaxNode node, MetricRule.Target target, boolean method, MetricRule rule);
}
