package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Finds the calls of overridable methods on the parameters of a method. */
final class ParameterUses {

  private static final int PARTS = 3;
  private static final String EXPRESSION = "expression";

  /** A call {@code parameter.name()} of a non-final method that returns a value. */
  record Use(String parameter, String name, String declaring, SyntaxNode node) {
    boolean sameCall(final Use other) {
      return parameter.equals(other.parameter)
          && name.equals(other.name)
          && declaring.equals(other.declaring);
    }
  }

  private ParameterUses() {}

  static Set<String> parameters(final SyntaxNode method) {
    final Set<String> names = new HashSet<>();
    JavaNodes.kid(method, "formalParameters")
        .ifPresent(
            list ->
                SyntaxWalk.descendants(list).stream()
                    .filter(node -> "variableDeclaratorId".equals(node.kind()))
                    .forEach(node -> names.add(SyntaxWalk.text(node))));
    return names;
  }

  static List<Use> in(
      final SyntaxNode scope, final Set<String> parameters, final UnitTypeFacts types) {
    return SyntaxWalk.descendants(scope).stream()
        .map(node -> use(node, parameters, types))
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<Use> use(
      final SyntaxNode node, final Set<String> parameters, final UnitTypeFacts types) {
    final List<SyntaxNode> parts = node.children();
    if (!EXPRESSION.equals(node.kind())
        || parts.size() != PARTS
        || !"methodCall".equals(parts.get(2).kind())
        || !parameters.contains(SyntaxWalk.text(parts.get(0)))) {
      return Optional.empty();
    }
    return types
        .callTarget(node.span())
        .filter(ParameterUses::overridableValue)
        .map(
            target ->
                new Use(
                    SyntaxWalk.text(parts.get(0)),
                    target.name(),
                    target.declaringType().name(),
                    node));
  }

  private static boolean overridableValue(final CallTarget target) {
    return !target.isStatic()
        && Boolean.FALSE.equals(target.isFinal())
        && target.returnType() != null
        && !"void".equals(target.returnType().name());
  }
}
