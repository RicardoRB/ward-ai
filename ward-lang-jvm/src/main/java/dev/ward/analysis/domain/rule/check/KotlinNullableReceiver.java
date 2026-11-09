package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.Nullness;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads the nullness of the name that a Kotlin postfix expression applies one operation to, as in
 * {@code name!!} or {@code name?.member}. The shape is the name followed by one operation, so a
 * longer chain is not read.
 */
final class KotlinNullableReceiver {
  static final String POSTFIX = "postfixUnaryExpression";
  static final String ATOMIC = "atomicExpression";
  static final String OPERATION = "postfixUnaryOperation";
  static final FactNeed NEED = new FactNeed(Set.of(ATOMIC), Set.of(FactKind.ANNOTATIONS));

  private static final int NAME = 0;
  private static final int OPERATOR = 1;
  private static final int PARTS = 2;

  private KotlinNullableReceiver() {}

  /**
   * Returns the nullness of the name that {@code postfix} applies one operation to, when the
   * operation's text satisfies {@code operation}; empty for any other shape.
   */
  static Optional<Nullness> nullnessOf(
      final SyntaxNode postfix, final UnitTypeFacts types, final Predicate<String> operation) {
    final List<SyntaxNode> parts = postfix.children();
    if (parts.size() != PARTS
        || !ATOMIC.equals(parts.get(NAME).kind())
        || !OPERATION.equals(parts.get(OPERATOR).kind())
        || !operation.test(SyntaxWalk.text(parts.get(OPERATOR)))) {
      return Optional.empty();
    }
    return Optional.of(types.nullness(parts.get(NAME).span()));
  }
}
