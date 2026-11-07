package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Shared builders and constants for the review check tests; the tests extend it. */
class TreeBuilders {

  static final String IDENTIFIER = "identifier";
  static final String TOKEN_IDENTIFIER = "IDENTIFIER";
  static final String CLASS_BODY = "classBody";
  static final String CLASS_BODY_DECLARATION = "classBodyDeclaration";
  static final String MEMBER_DECLARATION = "memberDeclaration";
  static final String METHOD_DECLARATION = "methodDeclaration";
  static final String MODIFIER = "modifier";
  static final String ANNOTATION = "annotation";
  static final String METHOD_CALL = "methodCall";
  static final String EXPRESSION = "expression";
  static final String PRIMARY = "primary";
  static final String STATEMENT = "statement";
  static final String BLOCK = "block";
  static final String FORMAL_PARAMETERS = "formalParameters";
  static final String FORMAL_PARAMETER = "formalParameter";
  static final String PARAMETER_ID = "variableDeclaratorId";
  static final String SWITCH = "'switch'";
  static final String TOKEN_SEMICOLON = "';'";
  static final String TOKEN_DOT = "'.'";
  static final String TOKEN_EQUALS = "'='";
  static final String TOKEN_OPEN = "'('";
  static final String TOKEN_CLOSE = "')'";
  static final String TEST_ANNOTATION = "@Test";
  static final String PUBLIC = "public";
  static final String PRIVATE = "private";
  static final String ABSTRACT = "abstract";
  static final String MESSAGE = "message {value}";
  static final SourceFile FILE = SourceFile.UNKNOWN;

  protected TreeBuilders() {}

  static SyntaxNode t(final String kind, final String text) {
    return Trees.leaf(kind, text, 0);
  }

  static SyntaxNode n(final String kind, final SyntaxNode... children) {
    return Trees.node(kind, 0, 1, 1, children);
  }

  static SyntaxNode id(final String name) {
    return n(IDENTIFIER, t(TOKEN_IDENTIFIER, name));
  }

  static SyntaxNode unit(final SyntaxNode declaration) {
    return Trees.unit(declaration);
  }

  static SyntaxNode assignment(final String target, final String value) {
    return n(
        EXPRESSION,
        n(EXPRESSION, n(PRIMARY, id(target))),
        t(TOKEN_EQUALS, "="),
        n(EXPRESSION, n(PRIMARY, id(value))));
  }

  static SyntaxNode argument(final String text) {
    return n(
        "arguments",
        t(TOKEN_OPEN, "("),
        n(EXPRESSION, t(TOKEN_IDENTIFIER, text)),
        t(TOKEN_CLOSE, ")"));
  }

  static SyntaxNode catchWithThrow(final SyntaxNode arguments) {
    return n(
        "catchClause",
        t("'catch'", "catch"),
        id("e"),
        n(
            BLOCK,
            n(
                STATEMENT,
                t("'throw'", "throw"),
                n(EXPRESSION, n("creator", id("IllegalStateException"), arguments)),
                t(TOKEN_SEMICOLON, ";"))));
  }

  static List<SyntaxNode> append(final List<SyntaxNode> list, final SyntaxNode node) {
    final List<SyntaxNode> out = new ArrayList<>(list);
    out.add(node);
    return out;
  }

  /** Returns the unit that holds one declaration, for tests that build trees step by step. */
  final SyntaxNode asUnit(final SyntaxNode declaration) {
    return unit(declaration);
  }
}
