package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.Span;
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
        .map(node -> new RuleMatch(reportedSpan(node), message))
        .toList();
  }

  /** The chain inside its parentheses, or the chain itself when it has no parentheses. */
  private static Span reportedSpan(final SyntaxNode node) {
    final SyntaxNode operand = node.children().get(0);
    return isParenthesizedChain(operand) ? operand.children().get(1).span() : operand.span();
  }

  private boolean isAssertedChain(final SyntaxNode node) {
    if (!SyntaxText.isToken(SyntaxText.last(node), ASSERTION) || node.children().size() < 2) {
      return false;
    }
    final SyntaxNode operand = node.children().get(0);
    return EXPRESSION.equals(operand.kind())
        && (isParenthesizedChain(operand) || hasOptionalTokens(operand));
  }

  private static boolean isParenthesizedChain(final SyntaxNode operand) {
    return !operand.children().isEmpty()
        && SyntaxText.isToken(operand.children().get(0), OPEN)
        && operand.children().size() > 2
        && isOptionalChain(operand.children().get(1));
  }

  private static boolean isOptionalChain(final SyntaxNode sequence) {
    if (sequence.children().isEmpty()) {
      return false;
    }
    SyntaxNode current = sequence.children().get(0);
    while (EXPRESSION.equals(current.kind())) {
      if (hasOptionalTokens(current)) {
        return true;
      }
      if (current.children().isEmpty()) {
        return false;
      }
      current = current.children().get(0);
    }
    return false;
  }

  private static boolean hasOptionalTokens(final SyntaxNode node) {
    return SyntaxText.hasToken(node, OPTIONAL)
        || (SyntaxText.hasToken(node, QUESTION) && SyntaxText.hasToken(node, DOT));
  }
}
