package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reports a {@code +} whose operands the policy does not allow. An object or array operand is never
 * allowed; the other kinds follow the policy, and an unknown operand is never reported.
 */
public final class RestrictOperandsCheck extends TypedCheck {

  private static final String PLUS = "+";
  private static final String PLUS_ASSIGN = "+=";
  private static final String OPERATOR = "assignmentOperator";

  private final OperandPolicy policy;
  private final String message;

  /** Creates the check with the rule's options and message. */
  public RestrictOperandsCheck(final OperandPolicy policy, final String message) {
    super();
    this.policy = policy;
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == SyntaxText.INFIX_PARTS && isPlus(parts)) {
        final ValueKind left = kindOf(types, parts.get(0));
        final ValueKind right = kindOf(types, parts.get(2));
        if (mixesBadly(left, right)) {
          out.add(new RuleMatch(node.span(), message));
        }
      }
    }
    return out;
  }

  /** Reports a plus, or a compound assignment with {@code +=} unless the policy skips it. */
  private boolean isPlus(final List<SyntaxNode> parts) {
    return SyntaxText.isToken(parts.get(1), PLUS)
        || OPERATOR.equals(parts.get(1).kind())
            && SyntaxText.hasToken(parts.get(1), PLUS_ASSIGN)
            && !policy.skipCompound();
  }

  private boolean mixesBadly(final ValueKind left, final ValueKind right) {
    if (isObjectKind(left) || isObjectKind(right)) {
      return true;
    }
    if (isMix(left, right, ValueKind.NUMBER, ValueKind.BIGINT)) {
      return true;
    }
    return disallowed(left) || disallowed(right) || numberAndString(left, right);
  }

  private static boolean isObjectKind(final ValueKind kind) {
    return kind == ValueKind.OBJECT || kind == ValueKind.ARRAY;
  }

  private boolean disallowed(final ValueKind kind) {
    return switch (kind) {
      case ANY -> !policy.allowAny();
      case BOOLEAN -> !policy.allowBoolean();
      case NULLISH -> !policy.allowNullish();
      case REGEXP -> !policy.allowRegExp();
      default -> false;
    };
  }

  private boolean numberAndString(final ValueKind left, final ValueKind right) {
    return isMix(left, right, ValueKind.NUMBER, ValueKind.STRING) && !policy.allowNumberAndString();
  }

  /** Reports whether the operands are one of each kind, in either order. */
  private static boolean isMix(
      final ValueKind left, final ValueKind right, final ValueKind one, final ValueKind other) {
    return left == one && right == other || left == other && right == one;
  }

  private static ValueKind kindOf(final UnitTypeFacts types, final SyntaxNode node) {
    final Optional<TypeRef> type = typeOf(types, node);
    return type.map(ValueKinds::of).orElse(ValueKind.OTHER);
  }
}
