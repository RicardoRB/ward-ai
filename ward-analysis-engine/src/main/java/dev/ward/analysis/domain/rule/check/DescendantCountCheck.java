package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Counts the descendants of each node of the given kinds whose kind is in {@code counted}, adds
 * {@code base}, and fails the node when the total is above the limit.
 */
public final class DescendantCountCheck implements RuleCheck {

  private final Set<String> kinds;
  private final CountedKinds counted;
  private final int base;
  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the node kinds to measure
   * @param counted the descendant kinds to count, and what cancels a count
   * @param base the value added to the count, for example 1 for cyclomatic complexity
   * @param max the largest allowed total
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code base} or {@code max} is negative
   */
  public DescendantCountCheck(
      final Set<String> kinds,
      final CountedKinds counted,
      final int base,
      final int max,
      final String message) {
    this.kinds = Set.copyOf(kinds);
    this.counted = Objects.requireNonNull(counted, "counted");
    this.base = CheckSupport.nonNegative(base, "base");
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, kinds)) {
      final SyntaxNode node = visit.node();
      final int value = base + count(node);
      if (value > max) {
        matches.add(
            new RuleMatch(
                node.span(),
                CheckSupport.fill(message, String.valueOf(value), String.valueOf(max))));
      }
    }
    return List.copyOf(matches);
  }

  private int count(final SyntaxNode node) {
    int total = 0;
    for (final SyntaxNode child : node.children()) {
      if (counts(node, child)) {
        total++;
      }
      total += count(child);
    }
    return total;
  }

  private boolean counts(final SyntaxNode parent, final SyntaxNode child) {
    return counted.kinds().contains(child.kind()) && !cancelled(parent);
  }

  /** Tells whether {@code parent} has the sibling kind that cancels a count beside it. */
  private boolean cancelled(final SyntaxNode parent) {
    return counted
        .unlessSibling()
        .map(kind -> parent.children().stream().anyMatch(c -> kind.equals(c.kind())))
        .orElse(false);
  }
}
