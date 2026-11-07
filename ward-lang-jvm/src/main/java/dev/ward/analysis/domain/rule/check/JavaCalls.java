package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.types.FactKind;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads the arguments of a call expression for the type-aware Java checks. */
final class JavaCalls {

  /** The needs of a check that reads the call targets and the types of expressions. */
  static final FactNeed CALLS_AND_TYPES =
      new FactNeed(Set.of("expression"), Set.of(FactKind.CALL, FactKind.TYPE));

  /** The needs of a check that also reads the shapes and the members of types. */
  static final FactNeed WITH_MEMBERS =
      new FactNeed(
          Set.of("expression"),
          Set.of(FactKind.CALL, FactKind.TYPE, FactKind.SHAPE, FactKind.MEMBERS));

  private JavaCalls() {}

  /** Returns the receiver expression of {@code call}, if it is written with one. */
  static Optional<SyntaxNode> receiver(final SyntaxNode call) {
    final List<SyntaxNode> parts = call.children();
    return !parts.isEmpty()
            && "expression".equals(parts.get(0).kind())
            && JavaNodes.kid(call, "methodCall").isPresent()
        ? Optional.of(parts.get(0))
        : Optional.empty();
  }

  /** Returns the argument expressions of {@code call}, empty when it is not a call. */
  static List<SyntaxNode> arguments(final SyntaxNode call) {
    return JavaNodes.kid(call, "methodCall")
        .flatMap(method -> JavaNodes.kid(method, "arguments"))
        .flatMap(arguments -> JavaNodes.kid(arguments, "expressionList"))
        .map(list -> JavaNodes.kids(list, "expression"))
        .orElse(List.of());
  }
}
