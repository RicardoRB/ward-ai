package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.ScopeView;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports {@code (T[]) collection.toArray()}: the no-argument {@code toArray()} of a {@code
 * java.util} collection returns an {@code Object[]}, which is not a {@code T[]}, so the cast always
 * throws. It reports nothing when the call is unknown.
 */
public final class ToArrayCastCheck implements RuleCheck {
  private static final String EXPRESSION = "expression";
  private static final String TYPE_TYPE = "typeType";
  private static final String OBJECT = "java.lang.Object";
  private static final String JAVA_UTIL = "java.util.";
  private static final int CAST_PARTS = 4;
  private static final int CAST_TYPE = 1;
  private static final int CAST_OPERAND = 3;
  private static final FactNeed NEED =
      new FactNeed(Set.of(EXPRESSION, TYPE_TYPE), Set.of(FactKind.CALL, FactKind.TYPE));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the cast
   */
  public ToArrayCastCheck(final String message) {
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
        .filter(cast -> castsToArray(cast, types))
        .map(cast -> CheckSupport.at(cast.span(), message))
        .toList();
  }

  private static boolean castsToArray(final SyntaxNode cast, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = cast.children();
    if (parts.size() != CAST_PARTS
        || !"(".equals(SyntaxWalk.text(parts.get(0)))
        || !TYPE_TYPE.equals(parts.get(CAST_TYPE).kind())) {
      return false;
    }
    final boolean typedArray =
        types
            .typeOf(parts.get(CAST_TYPE).span())
            .filter(type -> type.arrayDepth() > 0 && !OBJECT.equals(type.name()))
            .isPresent();
    return typedArray
        && types
            .callTarget(parts.get(CAST_OPERAND).span())
            .filter(ToArrayCastCheck::plainToArray)
            .isPresent();
  }

  private static boolean plainToArray(final CallTarget call) {
    return "toArray".equals(call.name())
        && call.parameters().isEmpty()
        && call.declaringType().name().startsWith(JAVA_UTIL)
        && call.returnType() != null
        && OBJECT.equals(call.returnType().name())
        && call.returnType().arrayDepth() == 1;
  }
}
