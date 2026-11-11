package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a member declared {@code public} whose type is not public, as PMD's {@code
 * PublicMemberInNonPublicType} does: the type, or a type around it, is not public, so the modifier
 * misleads. A member of an interface, a method annotated {@code @Override} (it may have to stay
 * public) and {@code main} are not reported. The compact constructor of a record is reported, the
 * record itself is not. A type without a known modifier list is taken as public.
 *
 * <p>shortcut: an overriding method without {@code @Override} is reported, because the overridden
 * method is not looked up.
 */
public final class PublicInNonPublicCheck implements UnitCheck {
  private static final String MEMBER = "classBodyDeclaration";
  private static final String INTERFACE_MEMBER = "interfaceBodyDeclaration";
  private static final String COMPACT = "compactConstructorDeclaration";
  private static final String RECORD = "recordDeclaration";
  private static final String OVERRIDE = "Override";
  private static final String MAIN = "main";
  private static final Set<String> TYPES =
      Set.of(
          MemberDeclarations.CLASS,
          "enumDeclaration",
          "recordDeclaration",
          "interfaceDeclaration",
          "annotationTypeDeclaration");
  private static final Set<String> INTERFACES =
      Set.of("interfaceDeclaration", "annotationTypeDeclaration");
  private static final Set<String> WRAPPERS = Set.of("typeDeclaration", MEMBER, INTERFACE_MEMBER);
  private static final Set<String> ANONYMOUS_PARENTS = Set.of("classCreatorRest", "enumConstant");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the public member
   */
  public PublicInNonPublicCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(MEMBER, COMPACT)).stream()
        .filter(
            visit -> MemberDeclarations.modifiers(visit.node()).contains(MemberDeclarations.PUBLIC))
        .filter(visit -> !exempt(visit.node()) && !isRecord(visit.node()))
        .filter(visit -> hiddenType(visit.ancestors()))
        .map(visit -> CheckSupport.at(publicModifier(visit.node()).span(), message))
        .toList();
  }

  /** The {@code public} modifier, where PMD anchors the finding when annotations come first. */
  private static SyntaxNode publicModifier(final SyntaxNode member) {
    return member.children().stream()
        .filter(child -> "public".equals(SyntaxWalk.text(child)))
        .findFirst()
        .orElse(member);
  }

  /** PMD reports the members of a record, not the record declaration itself. */
  private static boolean isRecord(final SyntaxNode member) {
    return MemberDeclarations.declaration(member)
        .filter(declaration -> RECORD.equals(declaration.kind()))
        .isPresent();
  }

  private static boolean exempt(final SyntaxNode member) {
    final boolean method =
        MemberDeclarations.declaration(member)
            .filter(d -> MemberDeclarations.METHOD.equals(d.kind()))
            .isPresent();
    return method && (JUnitMembers.annotations(member).contains(OVERRIDE) || isMain(member));
  }

  private static boolean isMain(final SyntaxNode member) {
    return MemberDeclarations.modifiers(member).contains(MemberDeclarations.STATIC)
        && MemberDeclarations.declaration(member)
            .filter(d -> MAIN.equals(MemberDeclarations.name(d)))
            .isPresent();
  }

  /** Whether the members of the nearest type are not visible everywhere, so public misleads. */
  private static boolean hiddenType(final List<SyntaxNode> up) {
    final int nearest = nearestType(up, up.size());
    if (nearest < 0) {
      return anonymousBody(up);
    }
    if (INTERFACES.contains(up.get(nearest).kind())) {
      return false;
    }
    for (int type = nearest; type >= 0; type = nearestType(up, type)) {
      if (declaredHidden(up, type)) {
        return true;
      }
    }
    return false;
  }

  private static int nearestType(final List<SyntaxNode> up, final int before) {
    for (int index = before - 1; index >= 0; index--) {
      if (TYPES.contains(up.get(index).kind())) {
        return index;
      }
    }
    return -1;
  }

  private static boolean anonymousBody(final List<SyntaxNode> up) {
    for (int index = up.size() - 1; index > 0; index--) {
      if (MemberDeclarations.CLASS_BODY.equals(up.get(index).kind())) {
        return ANONYMOUS_PARENTS.contains(up.get(index - 1).kind());
      }
    }
    return false;
  }

  /** Whether the type at {@code type} is declared without {@code public} in a non-interface. */
  private static boolean declaredHidden(final List<SyntaxNode> up, final int type) {
    for (int index = type - 1; index >= 0; index--) {
      final SyntaxNode wrapper = up.get(index);
      if (WRAPPERS.contains(wrapper.kind())) {
        return !INTERFACE_MEMBER.equals(wrapper.kind())
            && !MemberDeclarations.modifiers(wrapper).contains(MemberDeclarations.PUBLIC);
      }
    }
    return false;
  }
}
