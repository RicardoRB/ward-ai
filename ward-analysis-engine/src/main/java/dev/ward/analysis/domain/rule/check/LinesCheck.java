package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Fails each node of the given kinds that spans more lines than the limit. */
public final class LinesCheck implements RuleCheck {

  private final Set<String> kinds;
  private final int max;
  private final String message;
  private final boolean countEmpty;

  /**
   * Creates the check, which counts every line the node spans.
   *
   * @param kinds the node kinds to measure
   * @param max the largest allowed line count
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public LinesCheck(final Set<String> kinds, final int max, final String message) {
    this(kinds, max, message, true);
  }

  /**
   * Creates the check.
   *
   * @param kinds the node kinds to measure
   * @param max the largest allowed line count
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @param countEmpty whether blank and comment-only lines count; when not, only lines with code do
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public LinesCheck(
      final Set<String> kinds, final int max, final String message, final boolean countEmpty) {
    this.kinds = Set.copyOf(kinds);
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
    this.countEmpty = countEmpty;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, kinds)) {
      final SyntaxNode node = visit.node();
      final int lines = countEmpty ? spanLines(node) : codeLines(node);
      if (lines > max) {
        matches.add(
            new RuleMatch(
                node.span(),
                CheckSupport.fill(message, String.valueOf(lines), String.valueOf(max))));
      }
    }
    return List.copyOf(matches);
  }

  private static int spanLines(final SyntaxNode node) {
    return node.span().endLine() - node.span().startLine() + 1;
  }

  /** Counts the distinct lines that start a token of code; comments do not count. */
  private static int codeLines(final SyntaxNode node) {
    return (int)
        SyntaxWalk.leaves(node).stream().mapToInt(leaf -> leaf.span().startLine()).distinct().count();
  }
}
