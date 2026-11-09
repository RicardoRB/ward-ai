package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports three consecutive assignments that swap two names through a temporary, like Pylint's
 * {@code consider-swap-variables}: {@code tmp = a; a = b; b = tmp}. The finding sits at the first
 * assignment. Shortcut: each assignment must be a plain name on both sides.
 */
public final class SwapVariablesCheck implements RuleCheck {

  private static final Set<String> BLOCKS = Set.of("block", "file_input");
  private static final Set<String> STATEMENTS = Set.of("expr_stmt");
  private static final Pattern NAME = Pattern.compile("\\w+");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwapVariablesCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, BLOCKS)) {
      final List<SyntaxNode> assignments = new ArrayList<>();
      for (final SyntaxNode statement : visit.node().children()) {
        if ("stmt".equals(statement.kind())) {
          assignments.add(simpleAssignment(statement));
        } else {
          assignments.add(null);
        }
      }
      for (int index = 0; index + 2 < assignments.size(); index++) {
        final List<SyntaxNode> triple = assignments.subList(index, index + 3);
        if (triple.stream().allMatch(node -> node != null) && swaps(triple)) {
          found.add(new RuleMatch(triple.get(0).span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  /** The assignment of a plain name to a plain name in a statement, or null. */
  private static SyntaxNode simpleAssignment(final SyntaxNode statement) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(statement, STATEMENTS)) {
      final List<SyntaxNode> children = visit.node().children();
      if (children.size() == 3
          && "=".equals(SyntaxWalk.text(children.get(1)))
          && NAME.matcher(SyntaxWalk.text(children.get(0))).matches()
          && NAME.matcher(SyntaxWalk.text(children.get(2))).matches()) {
        return visit.node();
      }
      return null;
    }
    return null;
  }

  /** Whether the target of each assignment is the value of the previous one, in a rotation. */
  private static boolean swaps(final List<SyntaxNode> triple) {
    final String left0 = SyntaxWalk.text(triple.get(0).children().get(0));
    final String left1 = SyntaxWalk.text(triple.get(1).children().get(0));
    final String left2 = SyntaxWalk.text(triple.get(2).children().get(0));
    final String right0 = SyntaxWalk.text(triple.get(0).children().get(2));
    final String right1 = SyntaxWalk.text(triple.get(1).children().get(2));
    final String right2 = SyntaxWalk.text(triple.get(2).children().get(2));
    return left0.equals(right2) && left1.equals(right0) && left2.equals(right1);
  }
}
