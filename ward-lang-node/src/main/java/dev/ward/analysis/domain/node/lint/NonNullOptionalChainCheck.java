package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;

/** Reports a non-null assertion on a parenthesized optional chain, such as {@code (a?.b)!}. */
public final class NonNullOptionalChainCheck implements RuleCheck {

  private static final String ASSERTION = "!";
  private static final String OPTIONAL = "?.";
  private static final String QUESTION = "?";
  private static final String DOT = ".";
  private static final String OPEN = "(";
  private static final String EXPRESSION = "singleExpression";

  private final String message;

  /** Creates the check with the message of the rule. */
  public NonNullOptionalChainCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxText.nodesOf(root, EXPRESSION).stream()
        .filter(this::isAssertedChain)
        .map(node -> new RuleMatch(node.children().get(0).children().get(1).span(), message))
        .toList();
  }

  private boolean isAssertedChain(final SyntaxNode node) {
    if (!SyntaxText.isToken(SyntaxText.last(node), ASSERTION) || node.children().size() < 2) {
      return false;
    }
    final SyntaxNode operand = node.children().get(0);
    return EXPRESSION.equals(operand.kind())
        && !operand.children().isEmpty()
        && SyntaxText.isToken(operand.children().get(0), OPEN)
        && operand.children().size() > 2
        && isOptionalChain(operand.children().get(1));
  }

  private static boolean isOptionalChain(final SyntaxNode sequence) {
    final SyntaxNode start = sequence.children().isEmpty() ? null : sequence.children().get(0);
    SyntaxNode current = start;
    while (current != null && EXPRESSION.equals(current.kind())) {
      if (hasOptionalTokens(current)) {
        return true;
      }
      current = current.children().isEmpty() ? null : current.children().get(0);
    }
    return false;
  }

  private static boolean hasOptionalTokens(final SyntaxNode node) {
    return SyntaxText.hasToken(node, OPTIONAL)
        || (SyntaxText.hasToken(node, QUESTION) && SyntaxText.hasToken(node, DOT));
  }
}
