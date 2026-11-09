package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an {@code elif} or {@code else} whose preceding branch ends with the jump {@code jump}
 * ({@code return}, {@code raise}, {@code break} or {@code continue}), like pylint {@code
 * no-else-return} and its siblings: the branch after the jump needs no {@code else}.
 */
public final class ElseAfterJumpCheck implements RuleCheck {

  private static final Set<String> KINDS = Set.of("if_stmt");
  private static final String BLOCK = "block";
  private static final String ELIF = "elif";
  private static final String ELSE = "else";

  private final String jump;
  private final String message;

  /**
   * Creates the check.
   *
   * @param jump the jump keyword that ends the branch: return, raise, break or continue
   * @param message the message of each finding
   */
  public ElseAfterJumpCheck(final String jump, final String message) {
    this.jump = Objects.requireNonNull(jump, "jump");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, KINDS)) {
      branches(visit.node(), found);
    }
    return List.copyOf(found);
  }

  private void branches(final SyntaxNode ifNode, final List<RuleMatch> found) {
    Optional<SyntaxNode> lastBlock = Optional.empty();
    for (final SyntaxNode child : ifNode.children()) {
      if (BLOCK.equals(child.kind())) {
        lastBlock = Optional.of(child);
      } else if (lastBlock.isPresent() && isBranchKeyword(child)) {
        if (endsWithJump(lastBlock.get())) {
          found.add(new RuleMatch(child.span(), message));
        }
        lastBlock = Optional.empty();
      }
    }
  }

  private static boolean isBranchKeyword(final SyntaxNode child) {
    final String text = SyntaxWalk.text(child);
    return ELIF.equals(text) || ELSE.equals(text);
  }

  private boolean endsWithJump(final SyntaxNode block) {
    final List<SyntaxNode> statements = StatementShapes.statements(block);
    if (statements.isEmpty()) {
      return false;
    }
    final List<SyntaxNode> leaves = SyntaxWalk.leaves(statements.get(statements.size() - 1));
    return !leaves.isEmpty() && jump.equals(leaves.get(0).fields().get("text"));
  }
}
