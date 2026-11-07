package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an {@code if} without {@code elif} or {@code else} whose only statement is another such
 * {@code if}, like Ruff {@code SIM102}: the two conditions can be joined with {@code and}.
 */
public final class CollapsibleIfCheck implements RuleCheck {

  private static final Set<String> KINDS = Set.of("if_stmt");

  private final String message;

  /** Creates the check with the message of each finding. */
  public CollapsibleIfCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, KINDS)) {
      if (collapsible(visit.node())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean collapsible(final SyntaxNode outer) {
    return plain(outer)
        && StatementShapes.soleIf(StatementShapes.blocks(outer).get(0))
            .filter(CollapsibleIfCheck::plain)
            .isPresent();
  }

  private static boolean plain(final SyntaxNode node) {
    return !StatementShapes.hasKeyword(node, "elif") && !StatementShapes.hasKeyword(node, "else");
  }
}
