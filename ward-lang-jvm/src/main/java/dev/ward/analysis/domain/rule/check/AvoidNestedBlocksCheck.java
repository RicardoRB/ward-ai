package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a block nested directly in another block, as in {@code { { } }}. A nested block that is
 * the only statement of a switch case is allowed when {@code allowInSwitchCase} holds.
 */
public final class AvoidNestedBlocksCheck implements RuleCheck {

  private static final String BLOCK = "block";
  private static final String STATEMENT = "statement";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final String SWITCH_GROUP = "switchBlockStatementGroup";
  private static final int SWITCH_GROUP_DEPTH = 3;

  private final boolean allowInSwitchCase;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowInSwitchCase whether a nested block that is the only statement of a case is allowed
   * @param message the message
   */
  public AvoidNestedBlocksCheck(final boolean allowInSwitchCase, final String message) {
    this.allowInSwitchCase = allowInSwitchCase;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(BLOCK))) {
      final List<SyntaxNode> up = visit.ancestors();
      if (up.size() >= 2 && isNested(up) && !(allowInSwitchCase && onlyInCase(up))) {
        matches.add(CheckSupport.at(visit.node().span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isNested(final List<SyntaxNode> up) {
    final SyntaxNode parent = up.get(up.size() - 1);
    final SyntaxNode grandparent = up.get(up.size() - 2);
    return STATEMENT.equals(parent.kind()) && BLOCK_STATEMENT.equals(grandparent.kind());
  }

  private static boolean onlyInCase(final List<SyntaxNode> up) {
    if (up.size() < SWITCH_GROUP_DEPTH
        || !SWITCH_GROUP.equals(up.get(up.size() - SWITCH_GROUP_DEPTH).kind())) {
      return false;
    }
    return up.get(up.size() - SWITCH_GROUP_DEPTH).children().stream()
            .filter(child -> BLOCK_STATEMENT.equals(child.kind()))
            .count()
        == 1;
  }
}
