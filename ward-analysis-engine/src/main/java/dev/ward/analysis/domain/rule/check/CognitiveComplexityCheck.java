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
 * token adds one wherever it is. An {@code else if} chain does not nest: its branch keeps the
 * nesting of the chain. The node fails when its score is above the limit.
 */
public final class CognitiveComplexityCheck implements RuleCheck {

  private final Set<String> kinds;
  private final CognitiveTokens tokens;
  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param kinds the node kinds to score
   * @param tokens the structure, flat and chaining tokens
   * @param max the largest allowed score
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public CognitiveComplexityCheck(
      final Set<String> kinds, final CognitiveTokens tokens, final int max, final String message) {
    this.kinds = Set.copyOf(kinds);
    this.tokens = Objects.requireNonNull(tokens, "tokens");
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
    final List<SyntaxNode> children = node.children();
    final int inner = holdsStructure(node) ? nesting + 1 : nesting;
    int total = 0;
    for (int i = 0; i < children.size(); i++) {
      total += scoreChild(node, children, i, nesting, inner);
    }
    return total;
  }

  private int scoreChild(
      final SyntaxNode node,
      final List<SyntaxNode> children,
      final int i,
      final int nesting,
      final int inner) {
    final SyntaxNode child = children.get(i);
    if (tokens.structures().counts(node, child)) {
      return 1 + nesting;
    }
    if (tokens.flat().contains(child.kind())) {
      return 1;
    }
    if (tokens.chaining().contains(child.kind())) {
      return continuesChain(children, i) ? 0 : 1;
    }
    if (child.children().isEmpty()) {
      return 0;
    }
    return score(child, isChained(children, i) ? nesting : inner);
  }

  /** Tells whether the token at {@code i} is followed by a structure of its own. */
  private boolean continuesChain(final List<SyntaxNode> children, final int i) {
    return i + 1 < children.size() && holdsStructure(children.get(i + 1));
  }

  /** Tells whether the child at {@code i} is the branch of a chaining token, such as an else if. */
  private boolean isChained(final List<SyntaxNode> children, final int i) {
    return i > 0
        && tokens.chaining().contains(children.get(i - 1).kind())
        && holdsStructure(children.get(i));
  }

  private boolean holdsStructure(final SyntaxNode node) {
    return node.children().stream().anyMatch(c -> tokens.structures().counts(node, c));
  }
}
