package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports an expression statement that contains a call but is not a call, like Pylint's {@code
 * expression-not-assigned}: {@code x == f()} or {@code a or g()} does nothing with the call's
 * result. A bare call, a plain string, {@code ...}, {@code yield}, {@code await}, a walrus, and a
 * sole statement of a {@code try} body are not reported.
 */
public final class UnassignedCallExpressionCheck implements RuleCheck {

  private static final String STATEMENT = "expr_stmt";
  private static final String ATOM = "atom_expr";
  private static final String TRAILER = "trailer";
  private static final String TRY = "try_stmt";
  private static final String BLOCK = "block";
  private static final String STMT = "stmt";
  private static final Set<String> STATEMENTS = Set.of(STATEMENT);
  private static final Set<String> SKIPPED = Set.of("yield_expr", "namedexpr_test");
  private static final Pattern PLAIN_STRING =
      Pattern.compile("^[rRbBuU]{0,2}['\"].*", Pattern.DOTALL);
  private static final Pattern PREFIXED_FSTRING =
      Pattern.compile("^[rRbBuU]{0,2}[fF].*", Pattern.DOTALL);

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public UnassignedCallExpressionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, STATEMENTS)) {
      final SyntaxNode statement = visit.node();
      // An assignment has a target and an operator; a bare expression has only itself.
      if (statement.children().size() == 1
          && reported(statement.children().get(0), visit.ancestors())) {
        found.add(new RuleMatch(statement.span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean reported(
      final SyntaxNode statementChild, final List<SyntaxNode> ancestors) {
    final SyntaxNode expression = unwrap(statementChild);
    final String text = SyntaxWalk.text(expression);
    if (isPlainString(text)
        || "...".equals(text)
        || isSkippedKind(expression)
        || isBareCall(expression)) {
      return false;
    }
    return !soleStatementOfTry(ancestors) && containsCall(expression);
  }

  /** The node under a chain of single-child rule nodes, as the parser nests every expression. */
  static SyntaxNode unwrap(final SyntaxNode node) {
    SyntaxNode current = node;
    while (current.children().size() == 1) {
      current = current.children().get(0);
    }
    return current;
  }

  private static boolean isPlainString(final String text) {
    return PLAIN_STRING.matcher(text).matches() && !PREFIXED_FSTRING.matcher(text).matches();
  }

  private static boolean isSkippedKind(final SyntaxNode expression) {
    return SKIPPED.contains(expression.kind())
        || SyntaxWalk.text(expression).startsWith("await")
        || SyntaxWalk.text(expression).startsWith("yield");
  }

  /** A bare call: an atom whose last trailer is an argument list. */
  private static boolean isBareCall(final SyntaxNode node) {
    if (!ATOM.equals(node.kind()) || node.children().isEmpty()) {
      return false;
    }
    final SyntaxNode last = node.children().get(node.children().size() - 1);
    return TRAILER.equals(last.kind()) && SyntaxWalk.text(last).startsWith("(");
  }

  /** A call anywhere in the expression: an atom with an argument list among its trailers. */
  private static boolean containsCall(final SyntaxNode node) {
    if (ATOM.equals(node.kind())
        && node.children().stream()
            .anyMatch(
                child -> TRAILER.equals(child.kind()) && SyntaxWalk.text(child).startsWith("("))) {
      return true;
    }
    return node.children().stream().anyMatch(UnassignedCallExpressionCheck::containsCall);
  }

  /** The statement is the only statement of the body of a {@code try}. */
  private static boolean soleStatementOfTry(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode node = ancestors.get(index);
      if (BLOCK.equals(node.kind())) {
        final boolean onlyStatement =
            node.children().stream().filter(child -> STMT.equals(child.kind())).count() == 1;
        return onlyStatement && index > 0 && TRY.equals(ancestors.get(index - 1).kind());
      }
    }
    return false;
  }
}
