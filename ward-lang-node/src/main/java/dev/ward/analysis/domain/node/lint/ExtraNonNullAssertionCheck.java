package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.List;

/** Reports a non-null assertion applied to another non-null assertion, such as {@code a!!}. */
public final class ExtraNonNullAssertionCheck implements RuleCheck {

  private static final String ASSERTION = "!";
  private static final String EXPRESSION = "singleExpression";

  private final String message;

  /** Creates the check with the message of the rule. */
  public ExtraNonNullAssertionCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return SyntaxText.nodesOf(root, EXPRESSION).stream()
        .filter(this::isDoubleAssertion)
        .map(node -> new RuleMatch(node.span(), message))
        .toList();
  }

  private boolean isDoubleAssertion(final SyntaxNode node) {
    if (!SyntaxText.isToken(SyntaxText.last(node), ASSERTION) || node.children().size() < 2) {
      return false;
    }
    final SyntaxNode operand = node.children().get(0);
    return EXPRESSION.equals(operand.kind())
        && SyntaxText.isToken(SyntaxText.last(operand), ASSERTION);
  }
}
