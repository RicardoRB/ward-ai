package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tells a call of a method to itself from a call to an overload of the same name, without types. A
 * call with another number of arguments reaches an overload. When the type declares an overload
 * with the same number of parameters, only a call that passes the method's own parameters in order
 * is taken for a call to itself.
 */
final class SelfCall {
  private static final String METHOD = "methodDeclaration";
  private static final String IDENTIFIER = "identifier";
  private static final String PARAMETERS = "formalParameters";
  private static final Set<String> PARAMETER = Set.of("formalParameter", "lastFormalParameter");
  private static final Set<String> TYPE_BODIES =
      Set.of("classBody", "interfaceBody", "enumBodyDeclarations", "recordBody");
  private static final Set<String> OPENERS = Set.of("(", "[", "{");
  private static final Set<String> CLOSERS = Set.of(")", "]", "}");
  private static final String COMMA = ",";
  private static final String SPACE = " ";

  private SelfCall() {}

  /**
   * Tells whether the first call of {@code name} in {@code statement} can reach {@code method}.
   *
   * @param method the visit of the method declaration named {@code name}
   * @param name the method's name
   * @param statement the spaced text of the statement that calls {@code name}
   * @return false when the call reaches an overload
   */
  static boolean reachesItself(
      final SyntaxWalk.Visit method,
      final String name,
      final String statement
  ) {
    final List<String> parameters = parameters(method.node());
    final List<String> arguments = arguments(statement, name);
    if (arguments.size() != parameters.size()) {
      return false;
    }
    return arguments.equals(parameters) || !hasOverload(method, name, parameters.size());
  }

  /** The parameter names in order; each is the last token of its declaration. */
  private static List<String> parameters(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> PARAMETERS.equals(child.kind()))
        .flatMap(list -> SyntaxWalk.visits(list, PARAMETER).stream())
        .map(visit -> SyntaxWalk.leaves(visit.node()))
        .map(leaves -> SyntaxWalk.text(leaves.get(leaves.size() - 1)))
        .toList();
  }

  /** The spaced text of each argument of the first call of {@code name}. */
  private static List<String> arguments(final String statement, final String name) {
    final String open = name + " ( ";
    final int start = statement.indexOf(open);
    final List<String> arguments = new ArrayList<>();
    if (start < 0) {
      return arguments;
    }
    final StringBuilder current = new StringBuilder();
    int depth = 1;
    for (final String token : statement.substring(start + open.length()).split(SPACE)) {
      depth += OPENERS.contains(token) ? 1 : 0;
      depth -= CLOSERS.contains(token) ? 1 : 0;
      if (depth == 0) {
        break;
      }
      if (depth == 1 && COMMA.equals(token)) {
        arguments.add(current.toString());
        current.setLength(0);
      } else {
        current.append(current.isEmpty() ? "" : SPACE).append(token);
      }
    }
    if (!current.isEmpty()) {
      arguments.add(current.toString());
    }
    return arguments;
  }

  private static boolean hasOverload(
      final SyntaxWalk.Visit method,
      final String name,
      final int parameterCount
  ) {
    return method.ancestors().stream()
        .filter(ancestor -> TYPE_BODIES.contains(ancestor.kind()))
        .reduce((outer, inner) -> inner)
        .stream()
        .flatMap(body -> SyntaxWalk.visits(body, Set.of(METHOD)).stream())
        .map(SyntaxWalk.Visit::node)
        .filter(other -> other != method.node())
        .filter(other -> name.equals(nameOf(other)))
        .anyMatch(other -> parameters(other).size() == parameterCount);
  }

  private static String nameOf(final SyntaxNode method) {
    return method.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
