package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Shared builders and constants for the review check tests; the tests extend it. */
final class Declarations extends TreeBuilders {

  private Declarations() {
    super();
  }

  static SyntaxNode member(final boolean isAbstract) {
    final SyntaxNode modifier =
        isAbstract ? n(MODIFIER, t("'abstract'", ABSTRACT)) : n(MODIFIER, t("'final'", "final"));
    return n(
        CLASS_BODY_DECLARATION, modifier, n(MEMBER_DECLARATION, n(METHOD_DECLARATION, id("area"))));
  }

  static SyntaxNode abstractClass(final List<SyntaxNode> members) {
    final SyntaxNode body = n(CLASS_BODY, members.toArray(SyntaxNode[]::new));
    return unit(
        n(
            "typeDeclaration",
            n("classOrInterfaceModifier", t("'abstract'", ABSTRACT)),
            n("classDeclaration", t("'class'", "class"), id("Shape"), body)));
  }

  static SyntaxNode interfaceUnit(final List<SyntaxNode> modifiers, final List<String> methods) {
    final SyntaxNode[] bodies =
        methods.stream()
            .map(
                text ->
                    n(
                        "interfaceBodyDeclaration",
                        n(
                            "interfaceMemberDeclaration",
                            n("interfaceMethodDeclaration", t("'void'", "void"), id(text)))))
            .toArray(SyntaxNode[]::new);
    final SyntaxNode decl =
        n(
            "interfaceDeclaration",
            t("'interface'", "interface"),
            id("Greeter"),
            n("interfaceBody", bodies));
    final SyntaxNode wrapper = n("typeDeclaration", modifiers.toArray(SyntaxNode[]::new));
    return unit(
        new SyntaxNode(
            wrapper.kind(), wrapper.span(), wrapper.fields(), append(wrapper.children(), decl)));
  }

  static SyntaxNode classWith(final SyntaxNode... members) {
    return n("classDeclaration", t("'class'", "class"), id("Holder"), n(CLASS_BODY, members));
  }

  static SyntaxNode arrayField(final String name) {
    return field("int[]", name);
  }

  static SyntaxNode intField(final String name) {
    return field("int", name);
  }

  static SyntaxNode field(final String type, final String name) {
    final SyntaxNode typeNode =
        type.endsWith("[]")
            ? n("typeType", t("'int'", "int"), t("'['", "["), t("']'", "]"))
            : n("typeType", t("'int'", "int"));
    return n(
        CLASS_BODY_DECLARATION,
        n(
            MEMBER_DECLARATION,
            n(
                "fieldDeclaration",
                typeNode,
                n("variableDeclarators", n("variableDeclarator", n(PARAMETER_ID, id(name)))))));
  }

  static SyntaxNode returnOf(final String name) {
    return n(
        CLASS_BODY_DECLARATION,
        n(
            MEMBER_DECLARATION,
            n(
                METHOD_DECLARATION,
                id("values"),
                n(
                    "methodBody",
                    n(
                        BLOCK,
                        n(
                            "blockStatement",
                            n(
                                STATEMENT,
                                t("'return'", "return"),
                                n(EXPRESSION, n(PRIMARY, id(name))),
                                t(TOKEN_SEMICOLON, ";"))))))));
  }

  static SyntaxNode constructorStoring(final String field, final String param, final String value) {
    return n(
        CLASS_BODY_DECLARATION,
        n(
            MEMBER_DECLARATION,
            n(
                "constructorDeclaration",
                n(
                    FORMAL_PARAMETERS,
                    t(TOKEN_OPEN, "("),
                    n(
                        FORMAL_PARAMETER,
                        n("typeType", t("'int'", "int"), t("'['", "["), t("']'", "]")),
                        n(PARAMETER_ID, id(param))),
                    t(TOKEN_CLOSE, ")")),
                n(
                    BLOCK,
                    n(
                        STATEMENT,
                        n(
                            EXPRESSION,
                            n(EXPRESSION, t("'this'", "this"), t(TOKEN_DOT, "."), id(field)),
                            t(TOKEN_EQUALS, "="),
                            n(EXPRESSION, n(PRIMARY, id(value)))),
                        t(TOKEN_SEMICOLON, ";"))))));
  }

  static SyntaxNode privateMethod(final String name) {
    return n(
        CLASS_BODY_DECLARATION,
        n(MODIFIER, t("'private'", PRIVATE)),
        n(
            MEMBER_DECLARATION,
            n(
                METHOD_DECLARATION,
                t("'void'", "void"),
                id(name),
                n(FORMAL_PARAMETERS, t(TOKEN_OPEN, "("), t(TOKEN_CLOSE, ")")))));
  }
}
