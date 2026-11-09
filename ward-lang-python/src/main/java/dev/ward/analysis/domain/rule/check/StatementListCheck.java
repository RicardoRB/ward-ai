package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code pass} or an ellipsis statement that a block does not need: the block has another
 * statement, such as a docstring or code, besides it. A block whose only statement is the
 * placeholder is not reported.
 */
public final class StatementListCheck implements RuleCheck {

  /** The placeholder statement a block is reported for. */
  public enum Problem {
    /** A {@code pass} next to other statements. */
    UNNECESSARY_PASS,
    /** An ellipsis next to other statements. */
    UNNECESSARY_ELLIPSIS
  }

  private static final String BLOCK = "block";
  private static final int MIN_STATEMENTS = 1;

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the placeholder to report
   * @param message the message of each finding
   */
  public StatementListCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final String placeholder = problem == Problem.UNNECESSARY_PASS ? "pass" : "...";
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(BLOCK))) {
      final List<SyntaxNode> statements = StatementShapes.statements(visit.node());
      if (statements.size() > MIN_STATEMENTS) {
        statements.stream()
            .filter(statement -> placeholder.equals(SyntaxWalk.text(statement).strip()))
            .forEach(statement -> found.add(new RuleMatch(statement.span(), message)));
      }
    }
    return List.copyOf(found);
  }
}
