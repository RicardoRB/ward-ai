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
    final List<SyntaxNode> items =
        rule.keys().parameterPaths().stream().flatMap(path -> items(node, path).stream()).toList();
    return items.size() - receiver(items, method, rule);
  }

  /** Returns the parameter items that {@code path} reaches from {@code node}. */
  private static List<SyntaxNode> items(final SyntaxNode node, final List<String> path) {
    final List<SyntaxNode> containers = SyntaxTrees.follow(node, path.subList(0, path.size() - 1));
    return containers.stream()
        .flatMap(container -> SyntaxTrees.children(container, path.get(path.size() - 1)).stream())
        .toList();
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
