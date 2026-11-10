package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a unary minus whose operand is a known type that is neither a number, a bigint, {@code
 * never} nor {@code any}, which the minus cannot negate.
 */
public final class UnaryMinusCheck extends TypedCheck {

  private static final String MINUS = "-";
  private static final Set<ValueKind> NEGATABLE =
      Set.of(ValueKind.ANY, ValueKind.NUMBER, ValueKind.BIGINT, ValueKind.NEVER);

  private final String message;

  /** Creates the check with the message of the rule. */
  public UnaryMinusCheck(final String message) {
    super();
    this.message = message;
  }

  @Override
  protected Set<FactKind> kinds() {
    return Set.of(FactKind.TYPE);
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == 2
          && SyntaxText.isToken(parts.get(0), MINUS)
          && isUnsafe(types, parts.get(1))) {
        out.add(new RuleMatch(node.span(), message));
      }
    }
    return out;
  }

  private static boolean isUnsafe(final UnitTypeFacts types, final SyntaxNode operand) {
    final Optional<TypeRef> type = typeOf(types, operand);
    return type.isPresent() && !NEGATABLE.contains(ValueKinds.of(type.get()));
  }
}
