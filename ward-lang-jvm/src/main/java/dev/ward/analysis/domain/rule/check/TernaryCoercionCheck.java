package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeRef;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a conditional {@code c ? a : b} in which one branch is a boxed number and the other has
 * another numeric type, so the boxed value is unboxed and converted. It reports nothing when the
 * type of a branch is unknown.
 */
public final class TernaryCoercionCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String QUESTION = "'?'";
  private static final int PARTS = 5;
  private static final int WHEN_TRUE = 2;
  private static final int WHEN_FALSE = 4;
  private static final FactNeed NEED = new FactNeed(Set.of(EXPRESSION), Set.of(FactKind.TYPE));
  private static final Map<String, String> BOXES =
      Map.of(
          "java.lang.Byte", "byte",
          "java.lang.Short", "short",
          "java.lang.Character", "char",
          "java.lang.Integer", "int",
          "java.lang.Long", "long",
          "java.lang.Float", "float",
          "java.lang.Double", "double");
  private static final Set<String> PRIMITIVES = Set.copyOf(BOXES.values());

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the conditional
   */
  public TernaryCoercionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public FactNeed needs() {
    return NEED;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root,
      final FlowView flow,
      final ScopeView scopes,
      final UnitTypeFacts types,
      final SourceUnit unit) {
    return SyntaxWalk.visits(root, Set.of(EXPRESSION)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(node -> node.children().size() == PARTS && isConditional(node))
        .filter(node -> coerces(node, types))
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  private static boolean isConditional(final SyntaxNode node) {
    return QUESTION.equals(node.children().get(1).kind());
  }

  private static boolean coerces(final SyntaxNode conditional, final UnitTypeFacts types) {
    final Optional<TypeRef> whenTrue = type(conditional.children().get(WHEN_TRUE), types);
    final Optional<TypeRef> whenFalse = type(conditional.children().get(WHEN_FALSE), types);
    if (whenTrue.isEmpty() || whenFalse.isEmpty()) {
      return false;
    }
    final Optional<String> first = numeric(whenTrue.get());
    final Optional<String> second = numeric(whenFalse.get());
    return first.isPresent()
        && second.isPresent()
        && !first.equals(second)
        && (BOXES.containsKey(whenTrue.get().name()) || BOXES.containsKey(whenFalse.get().name()));
  }

  private static Optional<TypeRef> type(final SyntaxNode branch, final UnitTypeFacts types) {
    return types.typeOf(branch.span()).filter(type -> type.arrayDepth() == 0);
  }

  /** Returns the primitive a numeric type stands for, or nothing for any other type. */
  private static Optional<String> numeric(final TypeRef type) {
    if (BOXES.containsKey(type.name())) {
      return Optional.of(BOXES.get(type.name()));
    }
    return type.primitive() && PRIMITIVES.contains(type.name())
        ? Optional.of(type.name())
        : Optional.empty();
  }
}
