package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.CallTarget;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;
import java.util.Set;

/** Reads the shape of the arguments of a call against the facts of its resolved target. */
final class CallArgumentShapes {

  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String EXPRESSION_LIST = "expressionList";
  private static final String NULL = "null";
  private static final Set<String> NON_NULL = Set.of("NonNull", "Nonnull", "NotNull");

  private CallArgumentShapes() {}

  static boolean firstArgumentIsOfType(
      final Set<String> wanted, final List<SyntaxNode> arguments, final UnitTypeFacts types) {
    return !arguments.isEmpty()
        && types
            .typeOf(arguments.get(0).span())
            .filter(type -> wanted.contains(type.name()))
            .isPresent();
  }

  static boolean passesNullToNonNull(final List<SyntaxNode> arguments, final CallTarget target) {
    final List<List<String>> annotated = target.parameterAnnotations();
    for (int index = 0; index < arguments.size() && !annotated.isEmpty(); index++) {
      final List<String> names = annotated.get(Math.min(index, annotated.size() - 1));
      if (NULL.equals(SyntaxWalk.text(arguments.get(index)))
          && names.stream().anyMatch(CallArgumentShapes::isNonNull)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNonNull(final String annotation) {
    final String simple = annotation.substring(annotation.lastIndexOf('.') + 1);
    return NON_NULL.contains(simple);
  }

  static boolean fewVarargElements(
      final Set<String> wanted,
      final List<SyntaxNode> arguments,
      final CallTarget target,
      final UnitTypeFacts types) {
    if (!target.isVarargs() || target.parameters().isEmpty()) {
      return false;
    }
    final int elements = arguments.size() - (target.parameters().size() - 1);
    return elements == 0
        || elements == 1
            && types
                .typeOf(arguments.get(arguments.size() - 1).span())
                .filter(type -> type.arrayDepth() == 0)
                .filter(type -> wanted.contains(type.name()))
                .isPresent();
  }

  static boolean passesNullForVarargs(final List<SyntaxNode> arguments, final CallTarget target) {
    return target.isVarargs()
        && !arguments.isEmpty()
        && arguments.size() == target.parameters().size()
        && NULL.equals(SyntaxWalk.text(arguments.get(arguments.size() - 1)));
  }

  static List<SyntaxNode> arguments(final SyntaxNode call) {
    return call.children().stream()
        .filter(part -> METHOD_CALL.equals(part.kind()))
        .findFirst()
        .flatMap(
            invoked ->
                SyntaxWalk.descendants(invoked).stream()
                    .filter(node -> EXPRESSION_LIST.equals(node.kind()))
                    .findFirst())
        .map(
            list ->
                list.children().stream().filter(child -> EXPRESSION.equals(child.kind())).toList())
        .orElse(List.of());
  }
}
