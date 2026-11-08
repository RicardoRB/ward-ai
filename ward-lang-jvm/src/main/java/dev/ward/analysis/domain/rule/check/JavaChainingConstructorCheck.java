package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code this(...)} call that does not pass on a parameter of its constructor to a
 * parameter of the called constructor that has the same name and the same type, as Error Prone's
 * ChainingConstructorIgnoresParameter does. The called constructor is the one of the class that
 * takes that many arguments; with none or several of them the call is not checked. Two types are
 * the same here when their text is the same, which is narrower than Error Prone's assignability.
 */
public final class JavaChainingConstructorCheck implements RuleCheck {

  private static final String THIS = "'this'";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public JavaChainingConstructorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS))) {
      matches.addAll(report(visit.node()));
    }
    return List.copyOf(matches);
  }

  private List<RuleMatch> report(final SyntaxNode clazz) {
    final List<SyntaxNode> constructors =
        ClassShapes.constructors(clazz).stream().map(ClassMembers::declarationOf).toList();
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode caller : constructors) {
      final Map<String, String> own = parameters(caller);
      for (final SyntaxNode call : chained(caller)) {
        callee(constructors, arguments(call).size())
            .filter(target -> ignores(own, parameters(target), arguments(call)))
            .ifPresent(target -> matches.add(CheckSupport.at(call.span(), message)));
      }
    }
    return matches;
  }

  private static boolean ignores(
      final Map<String, String> own,
      final Map<String, String> target,
      final List<SyntaxNode> args) {
    final List<Map.Entry<String, String>> formals = List.copyOf(target.entrySet());
    for (int index = 0; index < formals.size() && index < args.size(); index++) {
      final Map.Entry<String, String> formal = formals.get(index);
      if (formal.getValue().equals(own.get(formal.getKey()))
          && !mentions(args.get(index), formal.getKey())) {
        return true;
      }
    }
    return false;
  }

  private static boolean mentions(final SyntaxNode argument, final String name) {
    return SyntaxWalk.descendants(argument).stream()
        .anyMatch(node -> "identifier".equals(node.kind()) && name.equals(SyntaxWalk.text(node)));
  }

  private static Optional<SyntaxNode> callee(final List<SyntaxNode> constructors, final int count) {
    final List<SyntaxNode> fits =
        constructors.stream()
            .filter(constructor -> parameters(constructor).size() == count)
            .toList();
    return fits.size() == 1 ? Optional.of(fits.get(0)) : Optional.empty();
  }

  private static List<SyntaxNode> chained(final SyntaxNode constructor) {
    return SyntaxWalk.descendants(constructor).stream()
        .filter(node -> "methodCall".equals(node.kind()))
        .filter(node -> !node.children().isEmpty() && THIS.equals(node.children().get(0).kind()))
        .toList();
  }

  private static List<SyntaxNode> arguments(final SyntaxNode call) {
    return JavaNodes.kid(call, "arguments")
        .flatMap(list -> JavaNodes.kid(list, "expressionList"))
        .map(list -> JavaNodes.kids(list, "expression"))
        .orElse(List.of());
  }

  /** The parameters of a constructor by name, in order; empty text for a type that varies. */
  private static Map<String, String> parameters(final SyntaxNode constructor) {
    final Map<String, String> found = new LinkedHashMap<>();
    JavaNodes.kid(constructor, "formalParameters")
        .ifPresent(
            list -> {
              final List<SyntaxNode> all = new ArrayList<>(JavaNodes.kids(list, "formalParameter"));
              JavaNodes.kids(list, "formalParameterList")
                  .forEach(rest -> all.addAll(JavaNodes.kids(rest, "formalParameter")));
              all.forEach(parameter -> found.put(name(parameter), type(parameter)));
            });
    return found;
  }

  private static String name(final SyntaxNode parameter) {
    return JavaNodes.kid(parameter, "variableDeclaratorId").map(SyntaxWalk::text).orElse("");
  }

  private static String type(final SyntaxNode parameter) {
    return JavaNodes.kid(parameter, "typeType").map(SyntaxWalk::text).orElse("");
  }
}
