package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Builders for the interface trees that the class design check tests run over. */
class DesignInterfaceTrees extends DesignMemberTrees {

  private static final String VOID = "void";
  private static final String INT = "int";

  protected DesignInterfaceTrees() {
    super();
  }

  static SyntaxNode interfaceNode(final String name, final SyntaxNode... members) {
    return n(
        "interfaceDeclaration",
        t("'interface'", "interface"),
        id(name),
        n("interfaceBody", members));
  }

  static SyntaxNode interfaceMethod(final String name) {
    return n(
        "interfaceBodyDeclaration",
        n(
            "interfaceMemberDeclaration",
            n("interfaceMethodDeclaration", t("'void'", VOID), id(name))));
  }

  static SyntaxNode interfaceConstant(final String name) {
    return n(
        "interfaceBodyDeclaration",
        n(
            "interfaceMemberDeclaration",
            n(
                "constDeclaration",
                n("typeType", t(TOKEN_IDENTIFIER, INT)),
                n("constantDeclarator", id(name)))));
  }

  static SyntaxNode sealedInterface(final String name, final boolean permits) {
    final List<SyntaxNode> children = new ArrayList<>();
    children.add(t("'interface'", "interface"));
    children.add(id(name));
    if (permits) {
      children.add(t("'permits'", "permits"));
    }
    children.add(n("interfaceBody"));
    return n(
        "typeDeclaration",
        n("classOrInterfaceModifier", t("'sealed'", "sealed")),
        n("interfaceDeclaration", children.toArray(SyntaxNode[]::new)));
  }
}
