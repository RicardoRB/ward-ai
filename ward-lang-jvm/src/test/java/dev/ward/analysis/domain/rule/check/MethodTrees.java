package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Shared builders and constants for the review check tests; the tests extend it. */
final class MethodTrees extends TreeBuilders {

  private MethodTrees() {
    super();
  }

  static SyntaxNode privateMethodWithCount() {
    return methodWithCount(PRIVATE);
  }

  static SyntaxNode publicMethodWithCount() {
    return methodWithCount(PUBLIC);
  }

  static SyntaxNode methodWithCount(final String modifier) {
    return n(
        CLASS_BODY_DECLARATION,
        n(MODIFIER, t("'" + modifier + "'", modifier)),
        n(
            MEMBER_DECLARATION,
            n(
                METHOD_DECLARATION,
                t("'void'", "void"),
                id("show"),
                n(
                    FORMAL_PARAMETERS,
                    t(TOKEN_OPEN, "("),
                    n(
                        FORMAL_PARAMETER,
                        n("typeType", t("'String'", "String")),
                        n(PARAMETER_ID, id("text"))),
                    t("','", ","),
                    n(
                        FORMAL_PARAMETER,
                        n("typeType", t("'int'", "int")),
                        n(PARAMETER_ID, id("count"))),
                    t(TOKEN_CLOSE, ")")),
                n(
                    "methodBody",
                    n(
                        BLOCK,
                        n(
                            STATEMENT,
                            t("'return'", "return"),
                            n(EXPRESSION, n(PRIMARY, id("text"))),
                            t(TOKEN_SEMICOLON, ";")))))));
  }

  static SyntaxNode countingLoop(final String body) {
    final boolean scales = body.contains("*");
    final SyntaxNode tail = scales ? n(EXPRESSION, t("'*'", "*"), id("i")) : n(EXPRESSION);
    return n(
        STATEMENT,
        t("'for'", "for"),
        n(
            "forControl",
            n(
                "forInit",
                n(
                    "localVariableDeclaration",
                    n(
                        "variableDeclarator",
                        n(PARAMETER_ID, id("i")),
                        t(TOKEN_EQUALS, "="),
                        t("0", "0")))),
            n(
                EXPRESSION,
                id("i"),
                t("'<'", "<"),
                id("values"),
                t(TOKEN_DOT, "."),
                t("'length'", "length")),
            n("expressionList", n(EXPRESSION, id("i"), t("'++'", "++")))),
        n(
            BLOCK,
            n(
                "blockStatement",
                n(
                    STATEMENT,
                    n(EXPRESSION, id("values"), t("'['", "["), id("i"), t("']'", "]")),
                    tail,
                    t(TOKEN_SEMICOLON, ";")))));
  }

  static SyntaxNode testClass(final List<SyntaxNode> members) {
    return n(
        "classDeclaration",
        t("'class'", "class"),
        id("CounterTest"),
        n(CLASS_BODY, members.toArray(SyntaxNode[]::new)));
  }

  static SyntaxNode namedClass(final String name, final List<SyntaxNode> members) {
    return n(
        "classDeclaration",
        t("'class'", "class"),
        id(name),
        n(CLASS_BODY, members.toArray(SyntaxNode[]::new)));
  }

  static SyntaxNode testMethod(final int assertions) {
    final SyntaxNode[] calls = new SyntaxNode[assertions];
    for (int i = 0; i < assertions; i++) {
      calls[i] =
          n(
              STATEMENT,
              n(
                  METHOD_CALL,
                  id("assertEquals"),
                  n("arguments", t(TOKEN_OPEN, "("), t(TOKEN_CLOSE, ")"))));
    }
    return n(
        CLASS_BODY_DECLARATION,
        n(MODIFIER, n(ANNOTATION, t("'@'", "@"), id("Test"))),
        n(
            MEMBER_DECLARATION,
            n(METHOD_DECLARATION, t("'void'", "void"), id("countsUp"), n(BLOCK, calls))));
  }

  static SyntaxNode publicTestMethod(final boolean annotated) {
    final SyntaxNode modifier =
        annotated
            ? n(
                MODIFIER,
                n(ANNOTATION, t("'@'", "@"), id("Test")),
                n(MODIFIER, t("'public'", PUBLIC)))
            : n(MODIFIER, t("'public'", PUBLIC));
    return n(
        CLASS_BODY_DECLARATION,
        modifier,
        n(MEMBER_DECLARATION, n(METHOD_DECLARATION, t("'void'", "void"), id("testCounts"))));
  }

  static SyntaxNode lifecycle(final String name, final boolean annotated) {
    final SyntaxNode modifier =
        annotated
            ? n(MODIFIER, n(ANNOTATION, t("'@'", "@"), id("AfterEach")))
            : n(MODIFIER, t("'void'", "void"));
    return n(
        CLASS_BODY_DECLARATION,
        modifier,
        n(
            MEMBER_DECLARATION,
            n(
                METHOD_DECLARATION,
                t("'void'", "void"),
                id(name),
                n(FORMAL_PARAMETERS, t(TOKEN_OPEN, "("), t(TOKEN_CLOSE, ")")))));
  }

  static SyntaxNode typeNamed(final String name) {
    return n(
        "typeDeclaration", n("classDeclaration", t("'class'", "class"), id(name), n(CLASS_BODY)));
  }
}
