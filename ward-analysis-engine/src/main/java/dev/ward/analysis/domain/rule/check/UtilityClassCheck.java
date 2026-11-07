package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each utility class that is instantiable: a concrete class without superclass or
 * interfaces, whose members are all static, that has a non-private member and no main method, and
 * that declares no private constructor.
 */
public final class UtilityClassCheck implements UnitCheck {

  private static final String STATIC_INITIALIZER_MARKER = "static";
  private static final String MAIN = "main";
  private static final Set<String> HIERARCHY = Set.of("extends", "implements");
  private static final String CONSTRUCTOR = MemberDeclarations.CONSTRUCTOR;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the class declaration
   */
  public UtilityClassCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .filter(visit -> isConcreteUtility(visit.node(), visit.ancestors()))
        .filter(visit -> !hasPrivateConstructor(visit.node()))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean isConcreteUtility(
      final SyntaxNode clazz, final List<SyntaxNode> ancestors) {
    final boolean abstractClass =
        MemberDeclarations.wrapper(ancestors)
            .map(wrapper -> MemberDeclarations.modifiers(wrapper).contains("abstract"))
            .orElse(false);
    final boolean hasSuperTypes =
        clazz.children().stream().anyMatch(child -> HIERARCHY.contains(SyntaxWalk.text(child)));
    return !abstractClass && !hasSuperTypes && isUtilityBody(clazz);
  }

  private static boolean isUtilityBody(final SyntaxNode clazz) {
    final List<SyntaxNode> members =
        MemberDeclarations.body(clazz).stream()
            .flatMap(body -> MemberDeclarations.members(body).stream())
            .toList();
    final boolean allStatic = members.stream().allMatch(UtilityClassCheck::isStatic);
    final boolean hasMain = members.stream().anyMatch(UtilityClassCheck::isMain);
    final boolean hasNonPrivate = members.stream().anyMatch(UtilityClassCheck::isNonPrivate);
    return allStatic && !hasMain && hasNonPrivate;
  }

  private static boolean isStatic(final SyntaxNode member) {
    final boolean initializer =
        member.children().stream()
            .anyMatch(c -> STATIC_INITIALIZER_MARKER.equals(SyntaxWalk.text(c)));
    return initializer || MemberDeclarations.modifiers(member).contains(MemberDeclarations.STATIC);
  }

  private static boolean isMain(final SyntaxNode member) {
    return MemberDeclarations.declaration(member)
        .filter(declaration -> MemberDeclarations.METHOD.equals(declaration.kind()))
        .map(MemberDeclarations::name)
        .filter(MAIN::equals)
        .isPresent();
  }

  private static boolean isNonPrivate(final SyntaxNode member) {
    return !MemberDeclarations.modifiers(member).contains(MemberDeclarations.PRIVATE);
  }

  private static boolean hasPrivateConstructor(final SyntaxNode clazz) {
    return MemberDeclarations.body(clazz).stream()
        .flatMap(body -> MemberDeclarations.members(body).stream())
        .filter(member -> MemberDeclarations.modifiers(member).contains(MemberDeclarations.PRIVATE))
        .anyMatch(
            member ->
                MemberDeclarations.declaration(member)
                    .map(declaration -> CONSTRUCTOR.equals(declaration.kind()))
                    .orElse(false));
  }
}
