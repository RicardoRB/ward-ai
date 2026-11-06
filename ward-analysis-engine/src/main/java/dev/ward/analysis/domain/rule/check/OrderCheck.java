package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports, in each node of the filter's kinds, every child that comes after a child ranked later in
 * {@code order}. A child ranks by the first entry of {@code order} equal to its kind or its text; a
 * child with no rank is ignored. The user option is {@code order}.
 */
public final class OrderCheck implements UnitCheck {

  private final NodeFilter filter;
  private final List<String> order;
  private final String message;

  /**
   * Creates the check.
   *
   * @param filter the nodes whose children are ordered
   * @param order kinds or texts, first to last
   * @param message the message; {@code {value}} is the text of the misplaced child
   */
  public OrderCheck(final NodeFilter filter, final List<String> order, final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.order = List.copyOf(order);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    filter.select(root, file).forEach(visit -> misplaced(visit.node(), matches));
    return List.copyOf(matches);
  }

  private void misplaced(final SyntaxNode parent, final List<RuleMatch> matches) {
    int highest = -1;
    for (final SyntaxNode child : parent.children()) {
      final int rank = rank(child);
      if (rank < 0) {
        continue;
      }
      if (rank < highest) {
        matches.add(
            CheckSupport.at(child.span(), CheckSupport.fill(message, SyntaxWalk.text(child), "")));
      }
      highest = Math.max(highest, rank);
    }
  }

  private int rank(final SyntaxNode child) {
    final String text = SyntaxWalk.text(child);
    for (int rank = 0; rank < order.size(); rank++) {
      if (order.get(rank).equals(text) || NodeFilter.selects(order.get(rank), child)) {
        return rank;
      }
    }
    return -1;
  }
}
