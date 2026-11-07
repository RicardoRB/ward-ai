package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Builders for the method and field trees that the class design check tests run over. */
class DesignMemberTrees extends DesignTrees {

  private static final String VOID = "void";
  private static final String PRIVATE = "private";

  protected DesignMemberTrees() {
    super();
  }

  static SyntaxNode method(final String name) {
    return n("methodDeclaration", t("'void'", VOID), id(name));
  }

  static SyntaxNode methodWithBody(final String name) {
    return n(
        "methodDeclaration",
        t("'void'", VOID),
        id(name),
        n(
            "methodBody",
            t("'{'", "{"),
            n("statement", t(TOKEN_IDENTIFIER, "run();")),
            t("'}'", "}")));
  }

  static SyntaxNode emptyMethod(final String name) {
    return n(
        "methodDeclaration",
        t("'void'", VOID),
        id(name),
        n("methodBody", t("'{'", "{"), t("'}'", "}")));
  }

  static SyntaxNode privateCtor(final String name) {
    return member(List.of(mod(PRIVATE)), constructor(name));
  }

  static SyntaxNode constructor(final String name) {
    return n("constructorDeclaration", id(name));
  }

  static SyntaxNode field(final String type, final String name) {
    return n(
        "fieldDeclaration",
        n("typeType", t(TOKEN_IDENTIFIER, type)),
        n("variableDeclarators", n("variableDeclarator", n("variableDeclaratorId", id(name)))));
  }

  static SyntaxNode throwing(final String name, final int count) {
    final SyntaxNode[] exceptions = new SyntaxNode[count];
    for (int index = 0; index < count; index++) {
      exceptions[index] = n("qualifiedName", id("E" + index));
    }
    return n("methodDeclaration", t("'void'", VOID), id(name), n("qualifiedNameList", exceptions));
  }
}
