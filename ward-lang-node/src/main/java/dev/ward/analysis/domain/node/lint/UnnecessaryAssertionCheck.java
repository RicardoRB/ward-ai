package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reports an {@code as} assertion to the type the expression already has. */
public final class UnnecessaryAssertionCheck extends TypedCheck {

  private static final String AS = "as";

  private final String message;

  /** Creates the check with the message of the rule. */
  public UnnecessaryAssertionCheck(final String message) {
    super();
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == SyntaxText.INFIX_PARTS && SyntaxText.isToken(parts.get(1), AS)) {
        final Optional<TypeRef> operand = typeOf(types, parts.get(0));
        if (operand.isPresent() && operand.get().display().equals(SyntaxText.of(parts.get(2)))) {
          out.add(new RuleMatch(node.span(), message));
        }
      }
    }
    return out;
  }
}
