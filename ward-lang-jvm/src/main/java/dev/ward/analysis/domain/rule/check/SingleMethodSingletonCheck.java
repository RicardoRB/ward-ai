package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports the second and every later {@code getInstance} method of a class, as PMD's
 * SingleMethodSingleton does: an overload does not return the cached instance, so each call makes a
 * new object. PMD narrows the rule to static methods that return the class; this check takes every
 * method of that name, so an instance method named getInstance is also counted.
 */
public final class SingleMethodSingletonCheck implements UnitCheck {

  private static final String GET_INSTANCE = "getInstance";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at each extra getInstance method
   */
  public SingleMethodSingletonCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .map(SyntaxWalk.Visit::node)
        .flatMap(clazz -> extras(clazz).stream())
        .map(method -> CheckSupport.at(method.span(), message))
        .toList();
  }

  private static List<SyntaxNode> extras(final SyntaxNode clazz) {
    final List<SyntaxNode> methods =
        ClassMembers.members(clazz).stream()
            .map(ClassMembers::declarationOf)
            .filter(declaration -> MemberDeclarations.METHOD.equals(declaration.kind()))
            .filter(declaration -> GET_INSTANCE.equals(MemberDeclarations.name(declaration)))
            .toList();
    return methods.size() < 2 ? List.of() : methods.subList(1, methods.size());
  }
}
