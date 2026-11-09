package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a loop that holds more {@code break} or {@code continue} statements than the limit, as
 * detekt's {@code LoopWithTooManyJumpStatements} does. The jumps of a nested loop belong to that
 * loop, so they are not counted for the outer one.
 */
public final class KotlinLoopJumpCheck implements RuleCheck {

  private static final String JUMP = "jumpExpression";
  private static final Set<String> LOOPS =
      Set.of("forExpression", "whileExpression", "doWhileExpression");
  private static final String VALUE = "{value}";
  private static final String LIMIT = "{max}";

  private final String message;
  private final int max;

  /**
   * Creates the check.
   *
   * @param message the message of each finding; {@code {value}} is the count and {@code {max}} the
   *     limit
   * @param max the largest allowed count of jumps in one loop
   */
  public KotlinLoopJumpCheck(final String message, final int max) {
    this.message = Objects.requireNonNull(message, "message");
    this.max = max;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, LOOPS)) {
      final int count = countJumps(visit.node());
      if (count > max) {
        found.add(
            new RuleMatch(
                visit.node().span(),
                message.replace(VALUE, String.valueOf(count)).replace(LIMIT, String.valueOf(max))));
      }
    }
    return List.copyOf(found);
  }

  private static int countJumps(final SyntaxNode node) {
    int total = 0;
    for (final SyntaxNode child : node.children()) {
      if (LOOPS.contains(child.kind())) {
        continue;
      }
      if (JUMP.equals(child.kind()) && breakOrContinue(child)) {
        total++;
      }
      total += countJumps(child);
    }
    return total;
  }

  private static boolean breakOrContinue(final SyntaxNode jump) {
    final String text = SyntaxWalk.text(jump);
    return text.startsWith("break") || text.startsWith("continue");
  }
}
