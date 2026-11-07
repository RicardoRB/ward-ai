package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the declaration trees the design checks read, shaped as the Java frontend shapes them. The
 * tests extend it, as the other check tests extend {@link TreeBuilders}.
 */
class DesignTrees extends TreeBuilders {

  private static final String CLASS_NAME = "Sample";

  protected DesignTrees() {
    super();
  }

  static SyntaxNode compilationUnit(final SyntaxNode... children) {
    return n("compilationunit", children);
  }

  static SyntaxNode topLevel(final String modifier, final SyntaxNode clazz) {
    final SyntaxNode wrapper =
        modifier == null
            ? n("typeDeclaration", clazz)
            : n(
                "typeDeclaration",
                n("classOrInterfaceModifier", t(quoted(modifier), modifier)),
                clazz);
    return compilationUnit(wrapper);
  }

  static SyntaxNode classOf(final SyntaxNode... members) {
    return classWith(List.of(), members);
  }

  static SyntaxNode classWith(final List<SyntaxNode> header, final SyntaxNode... members) {
    final List<SyntaxNode> children = new ArrayList<>();
    children.add(t("'class'", "class"));
    children.add(id(CLASS_NAME));
    children.addAll(header);
    children.add(n("classBody", members));
    return n("classDeclaration", children.toArray(SyntaxNode[]::new));
  }

  static SyntaxNode member(final SyntaxNode declaration, final String... modifiers) {
    final List<SyntaxNode> children = new ArrayList<>();
    for (final String text : modifiers) {
      children.add(n("modifier", t(quoted(text), text)));
    }
    children.add(n("memberDeclaration", declaration));
    return n("classBodyDeclaration", children.toArray(SyntaxNode[]::new));
  }

  static SyntaxNode method(final String name, final SyntaxNode... statements) {
    final SyntaxNode[] blockStatements =
        Arrays.stream(statements).map(s -> n("blockStatement", s)).toArray(SyntaxNode[]::new);
    return n("methodDeclaration", t("'void'", "void"), id(name), n("block", blockStatements));
  }

  static SyntaxNode constructor(final String... parameters) {
    return n("constructorDeclaration", id(CLASS_NAME), parameters(parameters));
  }

  static SyntaxNode field(final String name) {
    return n(
        "fieldDeclaration",
        t("'int'", "int"),
        n("variableDeclarator", n("variableDeclaratorId", id(name))));
  }

  static SyntaxNode parameters(final String... names) {
    final SyntaxNode[] params =
        Arrays.stream(names)
            .map(
                name ->
                    n("formalParameter", t("'int'", "int"), n("variableDeclaratorId", id(name))))
            .toArray(SyntaxNode[]::new);
    return n("formalParameters", params);
  }

  private static String quoted(final String text) {
    return "'" + text + "'";
  }
}
