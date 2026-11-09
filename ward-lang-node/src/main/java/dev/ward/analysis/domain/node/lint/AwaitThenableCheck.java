package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reports an {@code await} of a value whose known type is a primitive, which is not a thenable. */
public final class AwaitThenableCheck extends TypedCheck {

  private static final String AWAIT = "await";

  private final String message;

  /** Creates the check with the message of the rule. */
  public AwaitThenableCheck(final String message) {
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == 2 && SyntaxText.isToken(parts.get(0), AWAIT)) {
        final Optional<TypeRef> operand = typeOf(types, parts.get(1));
        if (operand.isPresent() && isPrimitive(operand.get()) && !isUntyped(operand.get())) {
          out.add(new RuleMatch(node.span(), message));
        }
      }
    }
    return out;
  }
}
