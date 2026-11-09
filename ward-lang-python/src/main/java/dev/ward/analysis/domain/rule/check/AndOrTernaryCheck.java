package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports an assignment or return of the form {@code cond and value or other} whose middle value is
 * a truthy literal, like Pylint's {@code consider-using-ternary}: the conditional expression says
 * the same thing. Other operands, and a pair of comparisons, are not reported.
 */
public final class AndOrTernaryCheck implements RuleCheck {

  private static final Set<String> STATEMENTS = Set.of("expr_stmt", "return_stmt");
  private static final Set<String> BOOLEAN = Set.of("and_test", "or_test");
  private static final String OR = "or";
  private static final String AND = "and";
  private static final String ASSIGN = "=";
  private static final String COMPARISON = "comparison";
  private static final Pattern TRUTHY = Pattern.compile("[1-9][0-9]*|True|'[^']+'|\"[^\"]+\"");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public AndOrTernaryCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, STATEMENTS)) {
      final SyntaxNode statement = visit.node();
      if (isAssignmentOrReturn(statement) && ternaryShaped(valueOf(statement))) {
        found.add(new RuleMatch(statement.span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean isAssignmentOrReturn(final SyntaxNode statement) {
    if ("return_stmt".equals(statement.kind())) {
      return statement.children().size() >= 2;
    }
    return statement.children().stream().anyMatch(child -> ASSIGN.equals(SyntaxWalk.text(child)));
  }

  private static SyntaxNode valueOf(final SyntaxNode statement) {
    final List<SyntaxNode> children = statement.children();
    return UnassignedCallExpressionCheck.unwrap(children.get(children.size() - 1));
  }

  /** {@code (cond and middle) or other}, each operand a single expression. */
  private static boolean ternaryShaped(final SyntaxNode value) {
    if (!isBoolean(value, "or_test") || !isOperator(value.children().get(1), OR)) {
      return false;
    }
    final SyntaxNode and = UnassignedCallExpressionCheck.unwrap(value.children().get(0));
    if (!isBoolean(and, "and_test") || !isOperator(and.children().get(1), AND)) {
      return false;
    }
    final SyntaxNode middle = UnassignedCallExpressionCheck.unwrap(and.children().get(2));
    final SyntaxNode other = UnassignedCallExpressionCheck.unwrap(value.children().get(2));
    if (BOOLEAN.contains(middle.kind()) || BOOLEAN.contains(other.kind())) {
      return false;
    }
    final boolean bothComparisons =
        COMPARISON.equals(middle.kind()) && COMPARISON.equals(other.kind());
    return !bothComparisons && TRUTHY.matcher(SyntaxWalk.text(middle)).matches();
  }

  /** A real boolean operation: its kind and three children, operand, operator and operand. */
  private static boolean isBoolean(final SyntaxNode node, final String kind) {
    return kind.equals(node.kind()) && node.children().size() == 3;
  }

  private static boolean isOperator(final SyntaxNode node, final String operator) {
    return operator.equals(SyntaxWalk.text(node));
  }
}
