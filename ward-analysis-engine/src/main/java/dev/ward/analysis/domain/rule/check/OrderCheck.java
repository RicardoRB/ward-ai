package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports, in each node of the filter's kinds, every child that comes after a child ranked later in
 * {@code order}. A child ranks by the first entry of {@code order} equal to its kind or its text; a
 * child with no rank is ignored. The user option is {@code order}.
 */
public final class OrderCheck implements UnitCheck {

  private final NodeFilter filter;
  private final List<String> order;
  private final Optional<GroupBy> groupBy;
  private final String message;

  /**
   * Creates the check.
   *
   * @param filter the nodes whose children are ordered
   * @param order kinds or texts, first to last
   * @param groupBy how to read a child's name to keep equal names together, if wanted
   * @param message the message; {@code {value}} is the text, or with a name the name, of the
   *     misplaced child
   * @throws IllegalArgumentException if there is neither an order nor a grouping
   */
  public OrderCheck(
      final NodeFilter filter,
      final List<String> order,
      final Optional<GroupBy> groupBy,
      final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.order = List.copyOf(order);
    this.groupBy = Objects.requireNonNull(groupBy, "groupBy");
    if (order.isEmpty() && groupBy.isEmpty()) {
      throw new IllegalArgumentException("an order needs an order list or a group-by");
    }
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : filter.select(root, file)) {
      if (!order.isEmpty()) {
        misplaced(visit.node(), matches);
      }
      groupBy.ifPresent(group -> scattered(visit.node(), group, matches));
    }
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

  private void scattered(
      final SyntaxNode parent, final GroupBy group, final List<RuleMatch> matches) {
    final Set<String> seen = new HashSet<>();
    String previous = null;
    for (final SyntaxNode child : parent.children()) {
      final Optional<String> name = takesPart(child) ? group.nameOf(child) : Optional.empty();
      if (name.isPresent()) {
        if (!name.get().equals(previous) && !seen.add(name.get())) {
          matches.add(CheckSupport.at(child.span(), CheckSupport.fill(message, name.get(), "")));
        }
        previous = name.get();
      }
    }
  }

  private boolean takesPart(final SyntaxNode child) {
    return order.isEmpty() || rank(child) >= 0;
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

  /**
   * How to read the name of a child: the text of its first node of {@code nameKind}, not entering
   * {@code skip} subtrees.
   *
   * @param nameKind the kind of the name node
   * @param skip the kinds whose subtrees hold no name, such as a return type
   */
  public record GroupBy(String nameKind, Set<String> skip) {

    /**
     * Copies the set.
     *
     * @throws NullPointerException if a field is missing
     */
    public GroupBy {
      Objects.requireNonNull(nameKind, "nameKind");
      skip = Set.copyOf(skip);
    }

    Optional<String> nameOf(final SyntaxNode child) {
      return SyntaxWalk.firstDescendant(child, nameKind, skip).map(SyntaxWalk::text);
    }
  }
}
