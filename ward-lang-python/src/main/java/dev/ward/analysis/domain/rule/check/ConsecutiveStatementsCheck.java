package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports statements whose neighbours decide the finding, like Pylint's {@code
 * consider-using-assignment-expr} and {@code confusing-consecutive-elif}.
 */
public final class ConsecutiveStatementsCheck implements RuleCheck {

  /** The shapes the check tells apart, one per rule. */
  public enum Problem {
    /** An assignment to a name followed by an {@code if} that tests that name. */
    ASSIGNMENT_EXPR,
    /** An {@code elif} after a branch whose last statement is an {@code if}. */
    CONFUSING_ELIF
  }

  private static final String BLOCK = "block";
  private static final String ELIF = "elif";
  private static final Set<String> BLOCKS = Set.of(BLOCK, "file_input");
  private static final Set<String> IFS = Set.of("if_stmt");
  private static final Pattern ASSIGNMENT = Pattern.compile("^(\\w+)=(?!=)");
  private static final String TEXT = "text";

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the shape to report
   * @param message the message of each finding
   */
  public ConsecutiveStatementsCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return switch (problem) {
      case ASSIGNMENT_EXPR -> assignmentThenIf(root);
      case CONFUSING_ELIF -> confusingElifs(root);
    };
  }

  private List<RuleMatch> assignmentThenIf(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, BLOCKS)) {
      final List<SyntaxNode> statements = StatementShapes.statements(visit.node());
      for (int index = 1; index < statements.size(); index++) {
        final Matcher assigned = ASSIGNMENT.matcher(SyntaxWalk.text(statements.get(index - 1)));
        if (assigned.find() && testsName(statements.get(index), assigned.group(1))) {
          found.add(new RuleMatch(statements.get(index).span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  /** Whether the statement is an {@code if} whose condition is the name or a test of it. */
  private static boolean testsName(final SyntaxNode statement, final String name) {
    return StatementShapes.asIf(statement)
        .map(
            node ->
                Pattern.compile(
                        "^if(?:not)?" + Pattern.quote(name) + "(?:is(?:not)?None|[=!]=[^:]+)?:")
                    .matcher(SyntaxWalk.text(node))
                    .find())
        .orElse(false);
  }

  private List<RuleMatch> confusingElifs(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, IFS)) {
      SyntaxNode previous = null;
      for (final SyntaxNode child : visit.node().children()) {
        if (BLOCK.equals(child.kind())) {
          previous = child;
        } else if (previous != null && ELIF.equals(child.fields().get(TEXT))) {
          endsWithIf(previous, child, found);
        }
      }
    }
    return List.copyOf(found);
  }

  private void endsWithIf(
      final SyntaxNode branch, final SyntaxNode keyword, final List<RuleMatch> found) {
    final List<SyntaxNode> statements = StatementShapes.statements(branch);
    if (!statements.isEmpty()
        && StatementShapes.asIf(statements.get(statements.size() - 1)).isPresent()) {
      found.add(new RuleMatch(keyword.span(), message));
    }
  }
}
