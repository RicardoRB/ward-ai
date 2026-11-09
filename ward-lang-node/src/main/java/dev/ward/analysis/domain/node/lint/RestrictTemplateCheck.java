package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reports a template placeholder whose known type the policy does not allow in a string. */
public final class RestrictTemplateCheck extends TypedCheck {

  private static final String OPEN = "${";

  private final TemplatePolicy policy;
  private final String message;

  /** Creates the check with the rule's options and message. */
  public RestrictTemplateCheck(final TemplatePolicy policy, final String message) {
    super();
    this.policy = policy;
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode atom : SyntaxText.nodesOf(root, TEMPLATE)) {
      final List<SyntaxNode> parts = atom.children();
      if (parts.size() == SyntaxText.INFIX_PARTS && SyntaxText.isToken(parts.get(0), OPEN)) {
        final Optional<TypeRef> type = typeOf(types, parts.get(1));
        if (type.isPresent() && isReported(type.get())) {
          out.add(new RuleMatch(parts.get(1).span(), message));
        }
      }
    }
    return out;
  }

  /** Reports whether a placeholder of this type breaks the policy. */
  private boolean isReported(final TypeRef type) {
    return !policy.allowedTypes().contains(type.name()) && !policy.allows(ValueKinds.of(type));
  }
}
