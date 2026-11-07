package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a static field that is not final in an EJB, as PMD's StaticEJBFieldShouldBeFinal does. A
 * class is an EJB when it carries a Stateless, Stateful, Singleton or MessageDriven annotation, or
 * implements an EJB interface; both are read from names, with or without a package.
 */
public final class StaticEjbFieldCheck implements UnitCheck {

  private static final Set<String> ANNOTATIONS =
      Set.of("@Stateless", "@Stateful", "@Singleton", "@MessageDriven");
  private static final Set<String> INTERFACES =
      Set.of("SessionBean", "EJBHome", "EJBLocalObject", "EJBLocalHome", "EJBObject");
  private static final String TYPE_LIST = "typeList";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the field
   */
  public StaticEjbFieldCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MemberDeclarations.CLASS)).stream()
        .filter(visit -> isEjb(visit.node(), visit.ancestors()))
        .flatMap(visit -> ClassMembers.members(visit.node()).stream())
        .filter(StaticEjbFieldCheck::isStaticNonFinalField)
        .map(member -> CheckSupport.at(ClassMembers.declarationOf(member).span(), message))
        .toList();
  }

  private static boolean isEjb(final SyntaxNode clazz, final List<SyntaxNode> ancestors) {
    return annotated(ancestors) || implementsEjb(clazz);
  }

  private static boolean annotated(final List<SyntaxNode> ancestors) {
    return MemberDeclarations.wrapper(ancestors).stream()
        .flatMap(wrapper -> MemberDeclarations.modifiers(wrapper).stream())
        .map(StaticEjbFieldCheck::annotationName)
        .anyMatch(ANNOTATIONS::contains);
  }

  /**
   * Drops the package of an annotation, so {@code @javax.ejb.Stateless} reads as
   * {@code @Stateless}.
   */
  private static String annotationName(final String annotation) {
    final String bare =
        annotation.contains("(") ? annotation.substring(0, annotation.indexOf('(')) : annotation;
    return "@" + ClassMembers.simpleName(bare.substring(1));
  }

  private static boolean implementsEjb(final SyntaxNode clazz) {
    return clazz.children().stream()
        .filter(child -> TYPE_LIST.equals(child.kind()))
        .flatMap(list -> SyntaxWalk.descendants(list).stream())
        .filter(node -> MemberDeclarations.IDENTIFIER.equals(node.kind()))
        .map(SyntaxWalk::text)
        .anyMatch(INTERFACES::contains);
  }

  private static boolean isStaticNonFinalField(final SyntaxNode member) {
    final List<String> modifiers = MemberDeclarations.modifiers(member);
    return MemberDeclarations.FIELD.equals(ClassMembers.declarationOf(member).kind())
        && modifiers.contains(MemberDeclarations.STATIC)
        && !modifiers.contains(MemberDeclarations.FINAL);
  }
}
