package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Counts the parameters of a function, leaving out the receiver of a method. */
final class ParameterCount implements Measure {

  @Override
  public Optional<Violation> check(
      final SyntaxNode node,
      final MetricRule.Target target,
      final boolean method,
      final MetricRule rule) {
    final int count = parameters(node, method, rule);
    if (count <= rule.max()) {
      return Optional.empty();
    }
    return Optional.of(
        new Violation(node.span(), SyntaxTrees.nameOf(node, rule.keys().name()), count, ""));
  }

  private static int parameters(
      final SyntaxNode node, final boolean method, final MetricRule rule) {
    final List<String> path = rule.keys().parameterPath();
    if (path.isEmpty()) {
      return 0;
    }
    final List<SyntaxNode> containers = SyntaxTrees.follow(node, path.subList(0, path.size() - 1));
    final List<SyntaxNode> items =
        containers.stream()
            .flatMap(
                container -> SyntaxTrees.children(container, path.get(path.size() - 1)).stream())
            .toList();
    return items.size() - receiver(items, method, rule);
  }

  private static int receiver(
      final List<SyntaxNode> items, final boolean method, final MetricRule rule) {
    final boolean first =
        !items.isEmpty()
            && rule.keys()
                .receivers()
                .contains(SyntaxTrees.nameOf(items.get(0), rule.keys().name()));
    return method && first ? 1 : 0;
  }
}
