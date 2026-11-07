package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the nested classes of a unit for the accessor rules. A private member of a nested class
 * needs a synthetic accessor only before Java 11, where nestmates share access; a release of zero
 * or less is unknown and such a rule reports nothing.
 */
final class AccessorRelease {

  /** The first release whose nestmates make the accessors unnecessary. */
  static final int NESTMATES = 11;

  private static final String CLASS_BODY = "classBody";
  private static final String MEMBER_DECLARATION = "memberDeclaration";
  private static final String CLASS_DECLARATION = "classDeclaration";

  private AccessorRelease() {}

  /** Returns whether a release below {@link #NESTMATES} is known. */
  static boolean applies(final int release) {
    return release > 0 && release < NESTMATES;
  }

  /** Returns every class declared inside a class body in {@code root}, in order. */
  static List<SyntaxNode> nestedClasses(final SyntaxNode root) {
    final List<SyntaxNode> nested = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS_BODY))) {
      for (final SyntaxNode member : JUnitMembers.members(visit.node())) {
        member.children().stream()
            .filter(child -> MEMBER_DECLARATION.equals(child.kind()))
            .flatMap(child -> child.children().stream())
            .filter(child -> CLASS_DECLARATION.equals(child.kind()))
            .forEach(nested::add);
      }
    }
    return List.copyOf(nested);
  }
}
