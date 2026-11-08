package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a constructor that contains a {@code throw} statement when its class is not final, is not
 * private and declares no final {@code finalize}: a subclass can then capture the partly built
 * object (a finalizer attack). A {@code throw} inside a lambda or a nested class is not counted.
 */
public final class ConstructorThrowCheck implements UnitCheck {
  private static final String CLASS_DECLARATION = "classDeclaration";
  private static final String CONSTRUCTOR = "constructorDeclaration";
  private static final String THROW = "'throw'";
  private static final Set<String> NESTED = Set.of("lambdaExpression", "classBody");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the constructor
   */
  public ConstructorThrowCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(CLASS_DECLARATION)).stream()
        .filter(visit -> !DeclaredModifiers.has(visit.ancestors(), "final"))
        .filter(visit -> !DeclaredModifiers.has(visit.ancestors(), "private"))
        .map(SyntaxWalk.Visit::node)
        .filter(clazz -> !hasFinalFinalize(clazz))
        .flatMap(clazz -> ClassMembers.callableMembers(clazz).stream())
        .filter(member -> !member.children().stream().anyMatch(ConstructorThrowCheck::isPrivate))
        .map(ClassMembers::declarationOf)
        .filter(declaration -> CONSTRUCTOR.equals(declaration.kind()) && throwsInBody(declaration))
        .map(constructor -> CheckSupport.at(constructor.span(), message))
        .toList();
  }

  private static boolean isPrivate(final SyntaxNode modifier) {
    return "private".equals(SyntaxWalk.text(modifier));
  }

  private static boolean hasFinalFinalize(final SyntaxNode clazz) {
    return ClassMembers.callableMembers(clazz).stream()
        .anyMatch(
            member ->
                member.children().stream().anyMatch(c -> "final".equals(SyntaxWalk.text(c)))
                    && "finalize"
                        .equals(MemberDeclarations.name(ClassMembers.declarationOf(member))));
  }

  private static boolean throwsInBody(final SyntaxNode constructor) {
    return throwing(constructor);
  }

  private static boolean throwing(final SyntaxNode node) {
    for (final SyntaxNode child : node.children()) {
      if (NESTED.contains(child.kind())) {
        continue;
      }
      if (THROW.equals(child.kind()) || throwing(child)) {
        return true;
      }
    }
    return false;
  }
}
