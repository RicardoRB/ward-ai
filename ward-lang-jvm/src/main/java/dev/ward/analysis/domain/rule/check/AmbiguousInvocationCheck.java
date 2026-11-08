package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FactNeed;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.FactKind;
import dev.ward.analysis.domain.types.TypeMembers;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an unqualified call inside a nested class that an enclosing class and a superclass of the
 * nested class both declare with the same number of parameters, so the reader cannot tell which one
 * runs. It reports nothing when the inherited methods of the nested class are unknown.
 */
public final class AmbiguousInvocationCheck extends FactsCheck {

  private static final String CLASS = "classDeclaration";
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";
  private static final String OBJECT = "java.lang.Object";
  private static final FactNeed NEED = new FactNeed(Set.of(CLASS), Set.of(FactKind.MEMBERS));

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the call
   */
  public AmbiguousInvocationCheck(final String message) {
    super(NEED);
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  List<RuleMatch> check(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS))) {
      final List<SyntaxNode> outer =
          visit.ancestors().stream().filter(a -> CLASS.equals(a.kind())).toList();
      final java.util.Optional<TypeMembers> members = types.membersOf(visit.node().span());
      if (!outer.isEmpty() && members.isPresent()) {
        calls(visit.node(), outer, members.get(), matches);
      }
    }
    return List.copyOf(matches);
  }

  private void calls(
      final SyntaxNode inner,
      final List<SyntaxNode> outer,
      final TypeMembers members,
      final List<RuleMatch> out) {
    final Set<String> own = new java.util.HashSet<>();
    DeclaredMethods.of(inner).forEach(method -> own.add(method.name()));
    for (final SyntaxNode call : SyntaxWalk.descendants(inner)) {
      if (EXPRESSION.equals(call.kind())
          && call.children().size() == 1
          && METHOD_CALL.equals(call.children().get(0).kind())) {
        final SyntaxNode invocation = call.children().get(0);
        final String name = JavaNodes.name(invocation);
        final int arguments = argumentCount(invocation);
        if (!own.contains(name)
            && inherits(members, name, arguments)
            && outer.stream().anyMatch(type -> declares(type, name, arguments))) {
          out.add(CheckSupport.at(call.span(), message));
        }
      }
    }
  }

  private static int argumentCount(final SyntaxNode invocation) {
    return SyntaxWalk.descendants(invocation).stream()
        .filter(node -> "expressionList".equals(node.kind()))
        .findFirst()
        .map(list -> JavaNodes.kids(list, EXPRESSION).size())
        .orElse(0);
  }

  private static boolean inherits(final TypeMembers members, final String name, final int count) {
    return members.inheritedMethods().stream()
        .anyMatch(
            method ->
                name.equals(method.name())
                    && method.parameters().size() == count
                    && !OBJECT.equals(method.declaringType().name()));
  }

  private static boolean declares(final SyntaxNode type, final String name, final int count) {
    return DeclaredMethods.of(type).stream()
        .anyMatch(method -> name.equals(method.name()) && method.parameterTypes().size() == count);
  }
}
