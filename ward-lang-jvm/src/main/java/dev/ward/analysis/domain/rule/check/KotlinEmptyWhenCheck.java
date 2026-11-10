package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code when} with no entries, as detekt's {@code empty-blocks} does. A {@code when}
 * that holds a comment is not empty, so the check looks at the comments as well as the entries.
 */
public final class KotlinEmptyWhenCheck implements RuleCheck {

  private static final String WHEN = "whenExpression";
  private static final String ENTRY = "whenEntry";
  private static final String COMMENT = "comment";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the {@code when}
   */
  public KotlinEmptyWhenCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(WHEN))) {
      final SyntaxNode when = visit.node();
      if (isEmpty(when)) {
        matches.add(CheckSupport.at(when.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isEmpty(final SyntaxNode when) {
    return when.children().stream().noneMatch(child -> ENTRY.equals(child.kind()))
        && SyntaxWalk.descendants(when).stream().noneMatch(node -> COMMENT.equals(node.kind()));
  }
}
