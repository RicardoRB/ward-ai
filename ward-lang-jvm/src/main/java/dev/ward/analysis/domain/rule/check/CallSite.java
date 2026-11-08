package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.ConstantValue;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Optional;

/**
 * A call expression {@code receiver.name(arguments)} with the method it resolves to.
 *
 * @param expression the call expression
 * @param target the resolved method
 * @param arguments the argument expressions
 * @param types the type facts of the file
 */
record CallSite(
    SyntaxNode expression, CallTarget target, List<SyntaxNode> arguments, UnitTypeFacts types) {

  private static final int PARTS = 3;

  /** Reads the call that {@code node} is, when it is one whose target is known. */
  static Optional<CallSite> of(final SyntaxNode node, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = node.children();
    if (!"expression".equals(node.kind())
        || parts.size() != PARTS
        || !"methodCall".equals(parts.get(2).kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> arguments =
        JavaNodes.kid(parts.get(2), "arguments")
            .flatMap(list -> JavaNodes.kid(list, "expressionList"))
            .map(list -> JavaNodes.kids(list, "expression"))
            .orElse(List.of());
    return types
        .callTarget(node.span())
        .map(target -> new CallSite(node, target, arguments, types));
  }

  /** Returns whether the method is {@code declaring.name} and static or not as given. */
  boolean is(final String declaring, final String name, final boolean statik) {
    return declaring.equals(target.declaringType().name())
        && name.equals(target.name())
        && statik == target.isStatic();
  }

  /** Returns the string constant of argument {@code index}, when it is one. */
  Optional<String> text(final int index) {
    return constant(index)
        .map(ConstantValue::value)
        .filter(String.class::isInstance)
        .map(String.class::cast);
  }

  /** Returns the compile-time constant of argument {@code index}, when it is one. */
  Optional<ConstantValue> constant(final int index) {
    return index < arguments.size()
        ? types.constant(arguments.get(index).span())
        : Optional.empty();
  }

  /** Returns the call that argument {@code index} is, when it is one whose target is known. */
  Optional<CallSite> argumentCall(final int index) {
    return index < arguments.size() ? of(arguments.get(index), types) : Optional.empty();
  }
}
