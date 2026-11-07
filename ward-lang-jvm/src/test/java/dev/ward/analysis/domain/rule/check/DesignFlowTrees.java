package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds the statement, package and import trees the design checks read. */
class DesignFlowTrees extends DesignTrees {

  protected DesignFlowTrees() {
    super();
  }

  static SyntaxNode ifStatement(final String condition, final SyntaxNode... inner) {
    return n(
        "statement",
        t("'if'", "if"),
        t("'('", "("),
        id(condition),
        t("')'", ")"),
        n("block", inner));
  }

  static SyntaxNode switchOf(final int labels, final int statements) {
    final List<SyntaxNode> children = new ArrayList<>();
    children.add(t("'switch'", "switch"));
    children.add(t("'('", "("));
    children.add(id("x"));
    children.add(t("')'", ")"));
    for (int index = 0; index < labels; index++) {
      children.add(
          n(
              "switchBlockStatementGroup",
              n("switchLabel", t("'case'", "case"), t("INT", "1"), t("':'", ":"))));
    }
    final List<SyntaxNode> blockStatements =
        Collections.nCopies(statements, n("blockStatement", id("x")));
    children.add(n("switchBlockStatementGroup", blockStatements.toArray(SyntaxNode[]::new)));
    return n("statement", children.toArray(SyntaxNode[]::new));
  }

  static SyntaxNode tryCatch(final String caught, final SyntaxNode thrown) {
    return n(
        "statement",
        t("'try'", "try"),
        n("block", thrown),
        n("catchClause", t("'catch'", "catch"), n("catchType", id(caught)), n("block")));
  }

  static SyntaxNode tryWithoutCatch(final SyntaxNode thrown) {
    return n("statement", t("'try'", "try"), n("block", thrown));
  }

  static SyntaxNode throwNew(final String type) {
    return n(
        "statement",
        t("'throw'", "throw"),
        t("'new'", "new"),
        n("createdName", id(type)),
        t("'('", "("),
        t("')'", ")"),
        t("';'", ";"));
  }

  static SyntaxNode packageDecl(final String name) {
    return n("packageDeclaration", t("'package'", "package"), t("NAME", name), t("';'", ";"));
  }

  static SyntaxNode importDecl(final String name) {
    return n("importDeclaration", t("'import'", "import"), t("NAME", name), t("';'", ";"));
  }

  static List<String> messagesOf(final UnitCheck check, final SyntaxNode root) {
    return Trees.messages(check.check(root, SourceFile.UNKNOWN));
  }
}
