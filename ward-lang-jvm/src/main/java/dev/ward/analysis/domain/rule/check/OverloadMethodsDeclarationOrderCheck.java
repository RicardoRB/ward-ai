package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports each method that has the name of an earlier method but is not declared right after a
 * method of that name, so that overloads are grouped together, as Checkstyle's
 * OverloadMethodsDeclarationOrder does. Other members do not break a group. With the user option,
 * overloads must also be in increasing order of parameter count.
 */
public final class OverloadMethodsDeclarationOrderCheck implements UnitCheck {

  private static final Set<String> BODIES = Set.of("classBody", "interfaceBody");
  private static final Set<String> METHODS =
      Set.of(
          "methodDeclaration",
          "genericMethodDeclaration",
          "interfaceMethodDeclaration",
          "genericInterfaceMethodDeclaration");
  private static final Set<String> NOT_ENTERED = Set.of("block", "classBody", "interfaceBody");
  private static final Set<String> NAME_SKIPPED =
      Set.of("typeTypeOrVoid", "typeType", "typeParameters", "formalParameters", "annotation");

  private final boolean orderByArity;
  private final String message;

  /**
   * Creates the check.
   *
   * @param orderByArity whether overloads must be in increasing order of parameter count
   * @param message the message reported at each misplaced method
   */
  public OverloadMethodsDeclarationOrderCheck(final boolean orderByArity, final String message) {
    this.orderByArity = orderByArity;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, BODIES)) {
      checkBody(visit.node(), matches);
    }
    return List.copyOf(matches);
  }

  private void checkBody(final SyntaxNode body, final List<RuleMatch> matches) {
    final Set<String> earlier = new HashSet<>();
    String previousName = null;
    int previousArity = -1;
    for (final SyntaxNode member : body.children()) {
      final Optional<SyntaxNode> method = methodOf(member);
      if (method.isEmpty()) {
        continue;
      }
      final String name = nameOf(method.get());
      final int arity = parameters(method.get());
      final boolean overload = name.equals(previousName);
      if (!overload && earlier.contains(name)
          || overload && orderByArity && arity < previousArity) {
        matches.add(CheckSupport.at(member.span(), message));
      }
      earlier.add(name);
      previousName = name;
      previousArity = arity;
    }
  }

  private static Optional<SyntaxNode> methodOf(final SyntaxNode member) {
    for (final String kind : METHODS) {
      final Optional<SyntaxNode> found = SyntaxWalk.firstDescendant(member, kind, NOT_ENTERED);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static String nameOf(final SyntaxNode method) {
    return SyntaxWalk.firstDescendant(method, "identifier", NAME_SKIPPED)
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static int parameters(final SyntaxNode method) {
    return SyntaxWalk.firstDescendant(method, "formalParameters", NOT_ENTERED)
        .map(
            formals ->
                (int)
                    SyntaxWalk.descendants(formals).stream()
                        .filter(node -> "formalParameter".equals(node.kind()))
                        .count())
        .orElse(0);
  }
}
