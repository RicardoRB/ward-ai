package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonStatementLeaves;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the statement that directly follows a {@code return}, {@code raise}, {@code break} or
 * {@code continue} in the same block, like Pylint {@code W0101}. Pylint looks only at the next
 * sibling, so a statement after an endless loop or after a branch that always returns is not
 * reported. A {@code yield} that ends a generator's body is not reported, and the statement after
 * it is reported instead.
 */
public final class UnreachableStatementCheck implements RuleCheck {

  private static final Set<String> JUMPS =
      Set.of("return_stmt", "raise_stmt", "break_stmt", "continue_stmt");
  private static final Set<String> LISTS = Set.of("file_input", "block");
  private static final String YIELD = "yield_stmt";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public UnreachableStatementCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found);
    return List.copyOf(found);
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root, final FlowView flow) {
    return matches(root);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> found) {
    if (LISTS.contains(node.kind())) {
      afterJumps(PythonStatementLeaves.leaves(node), found);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, found);
    }
  }

  private void afterJumps(final List<SyntaxNode> statements, final List<RuleMatch> found) {
    for (int index = 0; index < statements.size(); index++) {
      if (JUMPS.contains(statements.get(index).kind())) {
        final int next = nextReported(statements, index + 1);
        if (next < statements.size()) {
          found.add(new RuleMatch(statements.get(next).span(), message));
        }
      }
    }
  }

  /**
   * The index of the statement a jump reports: the next one, or the one after a generator yield.
   */
  private static int nextReported(final List<SyntaxNode> statements, final int next) {
    if (next < statements.size() && YIELD.equals(statements.get(next).kind())) {
      return next + 1 < statements.size() ? next + 1 : statements.size();
    }
    return next;
  }
}
