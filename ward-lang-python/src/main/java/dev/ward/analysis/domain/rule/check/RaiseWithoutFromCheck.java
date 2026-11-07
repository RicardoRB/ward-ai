package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code raise} with an exception inside an {@code except} handler that has no {@code
 * from} clause, like pyflakes {@code B904}. A bare re-raise is not reported.
 */
public final class RaiseWithoutFromCheck implements RuleCheck {

  private static final Set<String> TRIES = Set.of("try_stmt");
  private static final String HANDLER = "except_clause";
  private static final String BLOCK = "block";
  private static final String RAISE = "raise_stmt";
  private static final String BARE_RAISE = "raise";
  private static final String FROM = "from";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public RaiseWithoutFromCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TRIES)) {
      handlerBlocks(visit.node()).forEach(block -> addRaises(block, found));
    }
    return List.copyOf(found);
  }

  /** The body of each handler of a {@code try}: the block that follows an {@code except}. */
  private static List<SyntaxNode> handlerBlocks(final SyntaxNode node) {
    final List<SyntaxNode> blocks = new ArrayList<>();
    final List<SyntaxNode> children = node.children();
    for (int index = 0; index < children.size(); index++) {
      if (HANDLER.equals(children.get(index).kind())) {
        nextBlock(children, index).ifPresent(blocks::add);
      }
    }
    return blocks;
  }

  /** The first block after a handler; the grammar puts the colon between them. */
  private static Optional<SyntaxNode> nextBlock(
      final List<SyntaxNode> children, final int handler) {
    for (int index = handler + 1; index < children.size(); index++) {
      if (BLOCK.equals(children.get(index).kind())) {
        return Optional.of(children.get(index));
      }
      if (HANDLER.equals(children.get(index).kind())) {
        break;
      }
    }
    return Optional.empty();
  }

  private void addRaises(final SyntaxNode block, final List<RuleMatch> found) {
    for (final SyntaxNode node : SyntaxWalk.descendants(block)) {
      if (RAISE.equals(node.kind()) && chained(node)) {
        found.add(new RuleMatch(node.span(), message));
      }
    }
  }

  /** Whether a raise with an exception has no {@code from} clause. */
  private static boolean chained(final SyntaxNode raise) {
    final String text = SyntaxWalk.text(raise);
    return !BARE_RAISE.equals(text) && !text.contains(FROM);
  }
}
