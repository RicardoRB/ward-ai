package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The generic types a file declares with bounded type parameters, and the type variables it leaves
 * unbounded, so that a written type such as {@code Box<? super U>} can be told unsafe: its lower
 * bound is an unbounded type variable, which cannot be known to satisfy the bound of the parameter
 * it fills.
 */
final class WildcardTypes {

  private static final String TYPE_PARAMETER = "typeParameter";
  private static final String TYPE_TYPE = "typeType";
  private static final int BOUNDED = 3;
  private static final Set<String> DECLARATIONS =
      Set.of("classDeclaration", "interfaceDeclaration", "recordDeclaration");

  private final Map<String, List<String>> bounds = new HashMap<>();
  private final Set<String> unbounded = new HashSet<>();
  private final Set<String> variables = new HashSet<>();

  WildcardTypes(final SyntaxNode root) {
    final Set<String> bounded = new HashSet<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (TYPE_PARAMETER.equals(node.kind())) {
        final String name = NullOperands.methodName(node);
        variables.add(name);
        (bound(node).isEmpty() ? unbounded : bounded).add(name);
      }
      if (DECLARATIONS.contains(node.kind())) {
        bounds.put(NullOperands.methodName(node), parameterBounds(node));
      }
    }
    unbounded.removeAll(bounded);
  }

  private static List<String> parameterBounds(final SyntaxNode declaration) {
    final List<String> out = new ArrayList<>();
    NullSyntax.first(declaration, "typeParameters")
        .ifPresent(
            list ->
                list.children().stream()
                    .filter(child -> TYPE_PARAMETER.equals(child.kind()))
                    .forEach(parameter -> out.add(bound(parameter))));
    return out;
  }

  private static String bound(final SyntaxNode parameter) {
    return NullSyntax.first(parameter, "typeBound").map(SyntaxWalk::text).orElse("");
  }

  /** Tells whether the written type, or a type argument of it, has an unsafe wildcard. */
  boolean unsafe(final SyntaxNode type) {
    return SyntaxWalk.descendants(type).stream()
        .filter(node -> "classType".equals(node.kind()))
        .anyMatch(this::unsafeArguments);
  }

  private boolean unsafeArguments(final SyntaxNode type) {
    final List<String> declared =
        bounds.getOrDefault(
            NullSyntax.first(type, "typeIdentifier").map(SyntaxWalk::text).orElse(""), List.of());
    final List<SyntaxNode> arguments =
        NullSyntax.first(type, "typeArguments").stream()
            .flatMap(list -> list.children().stream())
            .filter(child -> "typeArgument".equals(child.kind()))
            .toList();
    for (int index = 0; index < arguments.size() && index < declared.size(); index++) {
      if (unsafeArgument(arguments.get(index), declared.get(index))) {
        return true;
      }
    }
    return false;
  }

  /** A {@code ? super U} argument where U is unbounded and the parameter has a real bound. */
  private boolean unsafeArgument(final SyntaxNode argument, final String bound) {
    final List<SyntaxNode> parts = argument.children();
    final boolean lower = parts.size() == BOUNDED && "super".equals(SyntaxWalk.text(parts.get(1)));
    return lower
        && TYPE_TYPE.equals(parts.get(2).kind())
        && unbounded.contains(SyntaxWalk.text(parts.get(2)))
        && !bound.isEmpty()
        && !bound.endsWith("Object")
        && !variables.contains(bound);
  }
}
