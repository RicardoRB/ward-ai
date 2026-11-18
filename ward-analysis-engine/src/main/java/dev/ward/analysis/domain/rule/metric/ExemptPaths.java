package dev.ward.analysis.domain.rule.metric;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** The ancestor paths below which a metric rule does not measure a node. */
final class ExemptPaths {
  private ExemptPaths() {}

  /**
   * Returns whether the nearest ancestors of a node, nearest first, follow one of the paths: a node
   * whose parent is {@code a} and whose grandparent is {@code b} is covered by {@code a/b}.
   */
  static boolean covers(final List<List<String>> paths, final Deque<SyntaxNode> ancestors) {
    return paths.stream().anyMatch(path -> follows(path, ancestors));
  }

  private static boolean follows(final List<String> path, final Deque<SyntaxNode> ancestors) {
    final Iterator<SyntaxNode> nearest = ancestors.iterator();
    for (final String kind : path) {
      if (!nearest.hasNext() || !nearest.next().kind().equals(kind)) {
        return false;
      }
    }
    return true;
  }
}
