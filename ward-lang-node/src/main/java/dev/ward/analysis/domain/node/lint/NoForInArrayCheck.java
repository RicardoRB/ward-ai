package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;

/** Reports a {@code for ... in} loop over an array, whose indices are strings and not elements. */
public final class NoForInArrayCheck extends TypedCheck {

  private static final String LOOP = "iterationStatement";
  private static final String IN = "in";

  private final String message;

  /** Creates the check with the message of the rule. */
  public NoForInArrayCheck(final String message) {
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode loop : SyntaxText.nodesOf(root, LOOP)) {
      final List<SyntaxNode> parts = loop.children();
      final int in = indexOf(parts, IN);
      if (in >= 0 && in + 1 < parts.size() && SEQUENCE.equals(parts.get(in + 1).kind())) {
        final boolean array =
            typeOf(types, parts.get(in + 1)).filter(TypedCheck::isArray).isPresent();
        if (array) {
          out.add(new RuleMatch(loop.span(), message));
        }
      }
    }
    return out;
  }

  private static int indexOf(final List<SyntaxNode> parts, final String token) {
    for (int index = 0; index < parts.size(); index++) {
      if (SyntaxText.isToken(parts.get(index), token)) {
        return index;
      }
    }
    return -1;
  }
}
