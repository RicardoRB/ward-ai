package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an equality between a value of an enum type and a value of a primitive type, which the
 * enum's members never equal in a type-safe way.
 */
public final class EnumComparisonCheck extends TypedCheck {

  private static final Set<String> EQUALITY = Set.of("==", "!=", "===", "!==");
  private static final Set<ValueKind> PRIMITIVE_KINDS =
      Set.of(ValueKind.NUMBER, ValueKind.STRING, ValueKind.BOOLEAN, ValueKind.BIGINT);

  private final String message;

  /** Creates the check with the message of the rule. */
  public EnumComparisonCheck(final String message) {
    super();
    this.message = message;
  }

  @Override
  protected Set<FactKind> kinds() {
    return Set.of(FactKind.TYPE, FactKind.SHAPE);
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode node : SyntaxText.nodesOf(root, EXPRESSION)) {
      final List<SyntaxNode> parts = node.children();
      if (parts.size() == SyntaxText.INFIX_PARTS
          && EQUALITY.contains(SyntaxText.of(parts.get(1)))
          && mixesEnumAndPrimitive(types, parts.get(0), parts.get(2))) {
        out.add(new RuleMatch(node.span(), message));
      }
    }
    return out;
  }

  private static boolean mixesEnumAndPrimitive(
      final UnitTypeFacts types, final SyntaxNode left, final SyntaxNode right) {
    return isEnum(types, left) && isPrimitive(types, right)
        || isEnum(types, right) && isPrimitive(types, left);
  }

  private static boolean isEnum(final UnitTypeFacts types, final SyntaxNode node) {
    final Optional<TypeShape> shape = types.shapeOf(node.span());
    return shape.isPresent() && shape.get().kind() == TypeShape.Kind.ENUM;
  }

  private static boolean isPrimitive(final UnitTypeFacts types, final SyntaxNode node) {
    final Optional<TypeRef> type = typeOf(types, node);
    return type.isPresent() && PRIMITIVE_KINDS.contains(ValueKinds.of(type.get()));
  }
}
