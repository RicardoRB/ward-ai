package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reports a {@code +} with an operand of a known object, array or function type, which the default
 * options never allow; a mix of string and number is allowed by default.
 */
public final class RestrictOperandsCheck extends TypedCheck {

  private static final String PLUS = "+";

  private final String message;

  /** Creates the check with the message of the rule. */
  public RestrictOperandsCheck(final String message) {
    super();
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == SyntaxText.INFIX_PARTS && SyntaxText.isToken(parts.get(1), PLUS)) {
        final Optional<TypeRef> left = typeOf(types, parts.get(0));
        final Optional<TypeRef> right = typeOf(types, parts.get(2));
        if (left.isPresent()
            && right.isPresent()
            && (isObjectLike(left.get()) || isObjectLike(right.get()))) {
          out.add(new RuleMatch(node.span(), message));
        }
      }
    }
    return out;
  }
}
