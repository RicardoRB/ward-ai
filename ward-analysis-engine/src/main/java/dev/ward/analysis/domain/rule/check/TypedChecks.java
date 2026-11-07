package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.TypeNeed;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeShape;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.Optional;
import java.util.Set;

/** Reads the type facts that the type-aware checks ask for, by node kind and fact kind. */
final class TypedChecks {

  /** The needs of a check that reads the calls of expressions. */
  static final TypeNeed CALLS = new TypeNeed(Set.of("expression"), Set.of(FactKind.CALL));

  /** The needs of a check that reads the type and shape of a switch selector. */
  static final TypeNeed ENUM_SELECTORS =
      new TypeNeed(Set.of("expression"), Set.of(FactKind.TYPE, FactKind.SHAPE));

  /** The needs of a check that reads the shapes of expressions and types. */
  static final TypeNeed SHAPES =
      new TypeNeed(Set.of("expression", "typeType"), Set.of(FactKind.SHAPE));

  private TypedChecks() {}

  /** Returns whether the expression or type at {@code node} is an enum. */
  static boolean isEnum(final SyntaxNode node, final UnitTypeFacts types) {
    return types
        .shapeOf(node.span())
        .map(TypeShape::kind)
        .filter(TypeShape.Kind.ENUM::equals)
        .isPresent();
  }

  /**
   * Returns the qualified name of the call's declaring type at {@code node}, if the call is known.
   */
  static Optional<String> declaringType(final SyntaxNode node, final UnitTypeFacts types) {
    return types.callTarget(node.span()).map(call -> call.declaringType().name());
  }

  /** Returns the name of the call at {@code node}, if the call is known. */
  static Optional<String> calledName(final SyntaxNode node, final UnitTypeFacts types) {
    return types.callTarget(node.span()).map(CallTarget::name);
  }
}
