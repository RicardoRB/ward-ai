package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads the header and the members of a class declaration for the SpotBugs ports. */
final class ClassShapes {

  private static final String VARIABLE_ID = "variableDeclaratorId";

  private ClassShapes() {}

  /** Returns the members of a class whose declaration is a constructor. */
  static List<SyntaxNode> constructors(final SyntaxNode clazz) {
    return ClassMembers.callableMembers(clazz).stream()
        .filter(member -> MemberDeclarations.CONSTRUCTOR.equals(declarationKind(member)))
        .toList();
  }

  /** Returns the members of a class whose declaration is a method. */
  static List<SyntaxNode> methods(final SyntaxNode clazz) {
    return ClassMembers.callableMembers(clazz).stream()
        .filter(member -> MemberDeclarations.METHOD.equals(declarationKind(member)))
        .toList();
  }

  /** Returns the initializer blocks of a class, static or not. */
  static List<SyntaxNode> initializers(final SyntaxNode clazz) {
    return ClassMembers.members(clazz).stream()
        .filter(member -> JavaNodes.kid(member, "block").isPresent())
        .toList();
  }

  /** Returns whether a constructor or method declaration takes no parameter. */
  static boolean takesNothing(final SyntaxNode declaration) {
    return JavaNodes.kid(declaration, "formalParameters")
        .map(list -> SyntaxWalk.descendants(list).stream().noneMatch(ClassShapes::isParameter))
        .orElse(true);
  }

  /** Returns the names a method or constructor declares as parameters or locals. */
  static Set<String> declaredNames(final SyntaxNode declaration) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode id : SyntaxWalk.descendants(declaration)) {
      if (VARIABLE_ID.equals(id.kind())) {
        names.add(SyntaxWalk.text(id));
      }
    }
    return names;
  }

  /** Returns whether the target of an assignment writes the field {@code name} of this object. */
  static boolean writes(final String target, final String name) {
    return target.equals(name) || target.equals("this." + name);
  }

  /** Returns whether a node starts before another. */
  static boolean before(final SyntaxNode first, final SyntaxNode second) {
    return first.span().startOffset() < second.span().startOffset();
  }

  private static boolean isParameter(final SyntaxNode node) {
    return "formalParameter".equals(node.kind()) || "receiverParameter".equals(node.kind());
  }

  private static String declarationKind(final SyntaxNode member) {
    return ClassMembers.declarationOf(member).kind();
  }
}
