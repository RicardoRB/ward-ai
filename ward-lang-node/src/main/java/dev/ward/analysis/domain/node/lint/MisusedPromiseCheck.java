package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;

/** Reports a promise used as an {@code if} condition, which is always truthy. */
public final class MisusedPromiseCheck extends TypedCheck {

  private final String message;

  /** Creates the check with the message of the rule. */
  public MisusedPromiseCheck(final String message) {
    super();
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode statement : SyntaxText.nodesOf(root, "ifStatement")) {
      for (final SyntaxNode condition : SyntaxText.childrenOf(statement, SEQUENCE)) {
        if (typeOf(types, condition).filter(TypedCheck::isPromise).isPresent()) {
          out.add(new RuleMatch(condition.span(), message));
        }
      }
    }
    return out;
  }
}
