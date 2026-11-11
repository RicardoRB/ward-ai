package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports parentheses that change nothing, as PMD's {@code UselessParentheses} does by default:
 * around a name, literal, call, member access, creation or other parentheses ({@code (x)}), and
 * around the whole value of a {@code return}, an initializer, an assignment or an argument.
 * Parentheses that only clarify precedence, such as {@code (a * b) + c}, are not reported.
 */
public final class UnnecessaryParenthesesCheck implements UnitCheck {
  private static final String PRIMARY = "primary";
  private static final String EXPRESSION = "expression";
  private static final String OPEN = "'('";
  private static final Set<String> WHOLE_VALUE_OWNERS =
      Set.of("variableInitializer", "expressionList");
  private static final Set<String> ASSIGNMENTS =
      Set.of(
          "'='", "'+='", "'-='", "'*='", "'/='", "'&='", "'|='", "'^='", "'>>='", "'>>>='", "'<<='",
          "'%='");
  private static final int WRAPPED = 3;
  private static final int INNER = 1;
  private static final int LAST = 2;
  private static final int MEMBER_PARTS = 3;
  private static final int INDEX_PARTS = 4;
  private static final int CREATION_PARTS = 2;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the parentheses
   */
  public UnnecessaryParenthesesCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(PRIMARY)).stream()
        .filter(visit -> parenthesized(visit.node()))
        .filter(visit -> atomic(visit.node().children().get(INNER)) || wholeValue(visit))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean parenthesized(final SyntaxNode primary) {
    return primary.children().size() == WRAPPED && OPEN.equals(primary.children().get(0).kind());
  }

  /** An expression whose parentheses never change the meaning, whatever surrounds it. */
  private static boolean atomic(final SyntaxNode expression) {
    final List<SyntaxNode> parts = expression.children();
    return EXPRESSION.equals(expression.kind())
        && (parts.size() == 1
            || parts.size() == MEMBER_PARTS && "'.'".equals(parts.get(1).kind())
            || parts.size() == INDEX_PARTS && "'['".equals(parts.get(1).kind())
            || parts.size() == CREATION_PARTS && "'new'".equals(parts.get(0).kind()));
  }

  /** Whether the parentheses wrap everything the surrounding statement or call takes. */
  private static boolean wholeValue(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> up = visit.ancestors();
    if (up.size() < LAST) {
      return false;
    }
    final SyntaxNode wrapper = up.get(up.size() - 1);
    final SyntaxNode owner = up.get(up.size() - LAST);
    return isReturn(owner, wrapper)
        || WHOLE_VALUE_OWNERS.contains(owner.kind())
        || assignedValue(owner, wrapper);
  }

  private static boolean isReturn(final SyntaxNode owner, final SyntaxNode wrapper) {
    return "statement".equals(owner.kind())
        && owner.children().size() > 1
        && "'return'".equals(owner.children().get(0).kind())
        && owner.children().get(1).equals(wrapper);
  }

  private static boolean assignedValue(final SyntaxNode owner, final SyntaxNode wrapper) {
    final List<SyntaxNode> parts = owner.children();
    return EXPRESSION.equals(owner.kind())
        && parts.size() == WRAPPED
        && ASSIGNMENTS.contains(parts.get(1).kind())
        && parts.get(LAST).equals(wrapper);
  }
}
