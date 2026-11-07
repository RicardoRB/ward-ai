package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the members of a class body: each member's modifiers and the declaration it wraps. A member
 * is a {@code classBodyDeclaration}; its declaration sits under a {@code memberDeclaration}.
 */
final class MemberDeclarations {

  static final String CLASS_BODY = "classBody";
  static final String CLASS = "classDeclaration";
  static final String METHOD = "methodDeclaration";
  static final String FIELD = "fieldDeclaration";
  static final String CONSTRUCTOR = "constructorDeclaration";
  static final String IDENTIFIER = "identifier";
  static final String PRIVATE = "private";
  static final String PUBLIC = "public";
  static final String PROTECTED = "protected";
  static final String FINAL = "final";
  static final String STATIC = "static";

  private static final String MEMBER = "classBodyDeclaration";
  private static final String DECLARATION = "memberDeclaration";
  private static final String TYPE_DECLARATION = "typeDeclaration";
  private static final Set<String> MODIFIER_KINDS = Set.of("modifier", "classOrInterfaceModifier");

  private MemberDeclarations() {}

  /** Returns the members of a class body, its {@code classBodyDeclaration} children. */
  static List<SyntaxNode> members(final SyntaxNode classBody) {
    return classBody.children().stream().filter(child -> MEMBER.equals(child.kind())).toList();
  }

  /** Returns the class body of a class declaration, when it has one. */
  static Optional<SyntaxNode> body(final SyntaxNode clazz) {
    return clazz.children().stream().filter(child -> CLASS_BODY.equals(child.kind())).findFirst();
  }

  /** Returns the declaration a member wraps, such as a method or a field. */
  static Optional<SyntaxNode> declaration(final SyntaxNode member) {
    return member.children().stream()
        .filter(child -> DECLARATION.equals(child.kind()))
        .flatMap(wrapper -> wrapper.children().stream())
        .findFirst();
  }

  /** Returns the texts of the modifiers a member, or a type declaration, has directly. */
  static List<String> modifiers(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> MODIFIER_KINDS.contains(child.kind()))
        .map(SyntaxWalk::text)
        .toList();
  }

  /** Returns the wrapper that carries the modifiers of a class declaration, when there is one. */
  static Optional<SyntaxNode> wrapper(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode ancestor = ancestors.get(index);
      if (MEMBER.equals(ancestor.kind()) || TYPE_DECLARATION.equals(ancestor.kind())) {
        return Optional.of(ancestor);
      }
    }
    return Optional.empty();
  }

  /** Returns the name of a method or constructor declaration. */
  static String name(final SyntaxNode declaration) {
    return declaration.children().stream()
        .filter(child -> IDENTIFIER.equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }
}
