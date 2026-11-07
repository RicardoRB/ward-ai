package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Lists the members of a class and the types and names its declarations carry. */
final class ClassMembers {

  private static final String IDENTIFIER = "identifier";
  private static final String TYPE_TYPE = "typeType";
  private static final String TYPE_TYPE_OR_VOID = "typeTypeOrVoid";
  private static final String VARIABLE_ID = "variableDeclaratorId";
  private static final Set<String> CALLABLES =
      Set.of("methodDeclaration", "constructorDeclaration");

  private ClassMembers() {}

  /** Returns the members of a class body, each a {@code classBodyDeclaration}. */
  static List<SyntaxNode> members(final SyntaxNode clazz) {
    return MemberDeclarations.body(clazz).stream()
        .flatMap(body -> MemberDeclarations.members(body).stream())
        .toList();
  }

  /** Returns the members of a class whose declaration is a method or a constructor. */
  static List<SyntaxNode> callableMembers(final SyntaxNode clazz) {
    return members(clazz).stream()
        .filter(member -> declarationKind(member).filter(CALLABLES::contains).isPresent())
        .toList();
  }

  /** Returns the members of a class whose declaration is a field. */
  static List<SyntaxNode> fieldMembers(final SyntaxNode clazz) {
    return members(clazz).stream()
        .filter(
            member -> declarationKind(member).filter(MemberDeclarations.FIELD::equals).isPresent())
        .toList();
  }

  /** Returns the declaration a member wraps, or the member itself when it wraps none. */
  static SyntaxNode declarationOf(final SyntaxNode member) {
    return MemberDeclarations.declaration(member).orElse(member);
  }

  /** Returns the names of the variables a field declares. */
  static List<String> fieldNames(final SyntaxNode field) {
    return SyntaxWalk.descendants(field).stream()
        .filter(node -> VARIABLE_ID.equals(node.kind()))
        .flatMap(id -> id.children().stream())
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .map(SyntaxWalk::text)
        .toList();
  }

  /** Returns the text of the type a declaration carries, when the frontend gave one. */
  static Optional<String> typeText(final SyntaxNode declaration) {
    return declaration.children().stream()
        .filter(child -> TYPE_TYPE.equals(child.kind()) || TYPE_TYPE_OR_VOID.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text);
  }

  /** Returns the last segment of a qualified type name, such as {@code Foo} for {@code a.b.Foo}. */
  static String simpleName(final String qualified) {
    return qualified.substring(qualified.lastIndexOf('.') + 1);
  }

  /** Returns the name of a class declaration, or an empty name when the node has none. */
  static String nameOf(final SyntaxNode clazz) {
    return MemberDeclarations.name(clazz);
  }

  private static Optional<String> declarationKind(final SyntaxNode member) {
    return MemberDeclarations.declaration(member).map(SyntaxNode::kind);
  }
}
