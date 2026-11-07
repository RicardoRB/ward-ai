package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Builders for the type and member trees that the class design check tests run over. */
class DesignTrees extends TreeBuilders {

  private static final int UNIT_LENGTH = 100;

  protected DesignTrees() {
    super();
  }

  static SyntaxNode root(final SyntaxNode... declarations) {
    return Trees.node("compilationunit", 0, UNIT_LENGTH, 1, declarations);
  }

  static SyntaxNode mod(final String text) {
    return n("modifier", t("'" + text + "'", text));
  }

  static SyntaxNode classDecl(final String name, final SyntaxNode... members) {
    return n("classDeclaration", t("'class'", "class"), id(name), n("classBody", members));
  }

  static SyntaxNode classNode(final String name, final SyntaxNode... members) {
    return typeDeclaration(List.of(), classDecl(name, members));
  }

  static SyntaxNode classNode(
      final List<SyntaxNode> modifiers, final String name, final SyntaxNode... members) {
    return typeDeclaration(modifiers, classDecl(name, members));
  }

  static SyntaxNode classWithSuper(
      final String name, final String superName, final SyntaxNode... members) {
    final SyntaxNode decl =
        n(
            "classDeclaration",
            t("'class'", "class"),
            id(name),
            n("typeType", t(TOKEN_IDENTIFIER, superName)),
            n("classBody", members));
    return typeDeclaration(List.of(), decl);
  }

  static SyntaxNode typeDeclaration(final List<SyntaxNode> modifiers, final SyntaxNode decl) {
    final List<SyntaxNode> children = new ArrayList<>(modifiers);
    children.add(decl);
    return n("typeDeclaration", children.toArray(SyntaxNode[]::new));
  }

  static SyntaxNode member(final List<SyntaxNode> modifiers, final SyntaxNode declaration) {
    final List<SyntaxNode> children = new ArrayList<>(modifiers);
    children.add(n("memberDeclaration", declaration));
    return n("classBodyDeclaration", children.toArray(SyntaxNode[]::new));
  }

  static SyntaxNode innerMember() {
    return member(List.of(), classDecl("Inner"));
  }

  static SyntaxNode comment(final String text) {
    return Trees.leaf("comment", text, 0);
  }
}
