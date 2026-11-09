package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports a {@code delete} of an element of an array, which leaves a hole instead of removing it.
 */
public final class NoArrayDeleteCheck extends TypedCheck {

  private static final String DELETE = "delete";
  private static final String OPEN_INDEX = "[";

  private final String message;

  /** Creates the check with the message of the rule. */
  public NoArrayDeleteCheck(final String message) {
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == 2 && SyntaxText.isToken(parts.get(0), DELETE)) {
        final List<SyntaxNode> target = parts.get(1).children();
        if (target.size() >= 2
            && EXPRESSION.equals(target.get(0).kind())
            && SyntaxText.of(target.get(1)).startsWith(OPEN_INDEX)
            && typeOf(types, target.get(0)).filter(TypedCheck::isArray).isPresent()) {
          out.add(new RuleMatch(node.span(), message));
        }
      }
    }
    return out;
  }
}
