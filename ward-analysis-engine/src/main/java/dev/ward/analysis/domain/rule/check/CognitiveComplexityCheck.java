package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Scores each node of the given kinds with a nesting-aware cognitive complexity. A structure token
 * adds one plus the current nesting, and the children of its node are one level deeper. A flat
 * token adds one wherever it is. The node fails when its score is above the limit.
 */
public final class CognitiveComplexityCheck implements RuleCheck {

  private final Set<String> kinds;
  private final Set<String> structures;
  private final Set<String> flat;
  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the node kinds to score
   * @param structures the token kinds that nest, such as an {@code if} keyword
   * @param flat the token kinds that add one, such as {@code &&}
   * @param max the largest allowed score
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public CognitiveComplexityCheck(
      final Set<String> kinds,
      final Set<String> structures,
      final Set<String> flat,
      final int max,
      final String message) {
    this.kinds = Set.copyOf(kinds);
    this.structures = Set.copyOf(structures);
    this.flat = Set.copyOf(flat);
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, kinds)) {
      final SyntaxNode node = visit.node();
      final int score = score(node, 0);
      if (score > max) {
        matches.add(
            new RuleMatch(
                node.span(),
                CheckSupport.fill(message, String.valueOf(score), String.valueOf(max))));
      }
    }
    return List.copyOf(matches);
  }

  private int score(final SyntaxNode node, final int nesting) {
    final boolean nests = node.children().stream().anyMatch(c -> structures.contains(c.kind()));
    final int inner = nests ? nesting + 1 : nesting;
    int total = 0;
    for (final SyntaxNode child : node.children()) {
      if (structures.contains(child.kind())) {
        total += 1 + nesting;
      } else if (flat.contains(child.kind())) {
        total += 1;
      } else if (!child.children().isEmpty()) {
        total += score(child, inner);
      }
    }
    return total;
  }
}
