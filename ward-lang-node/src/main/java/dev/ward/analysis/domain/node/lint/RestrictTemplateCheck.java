package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reports a template placeholder whose known type is an object, array or function. */
public final class RestrictTemplateCheck extends TypedCheck {

  private static final String OPEN = "${";

  private final String message;

  /** Creates the check with the message of the rule. */
  public RestrictTemplateCheck(final String message) {
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode atom : SyntaxText.nodesOf(root, TEMPLATE)) {
      final List<SyntaxNode> parts = atom.children();
      if (parts.size() == 3 && SyntaxText.isToken(parts.get(0), OPEN)) {
        final Optional<TypeRef> type = typeOf(types, parts.get(1));
        if (type.isPresent() && isObjectLike(type.get())) {
          out.add(new RuleMatch(parts.get(1).span(), message));
        }
      }
    }
    return out;
  }
}
