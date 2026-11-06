package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** The node kinds of the Python grammar that the type resolver reads. */
final class PythonTypeKinds {

  /** The kind of a token node for an identifier. */
  static final String TOKEN_NAME = "NAME";

  /** The kind of a numeric literal token. */
  static final String NUMBER = "NUMBER";

  /** The kind of a string literal token. */
  static final String STRING = "STRING";

  /** The kind of a comment node. */
  static final String COMMENT = "comment";

  static final String ATOM = "atom";
  static final String ATOM_EXPR = "atom_expr";
  static final String TRAILER = "trailer";
  static final String ARGLIST = "arglist";
  static final String ARGUMENT = "argument";
  static final String TFPDEF = "tfpdef";
  static final String TYPEDARGSLIST = "typedargslist";
  static final String PARAMETERS = "parameters";
  static final String EXPR_STMT = "expr_stmt";
  static final String ANNASSIGN = "annassign";
  static final String FUNCDEF = "funcdef";
  static final String CLASSDEF = "classdef";
  static final String DECORATED = "decorated";
  static final String STMT = "stmt";
  static final String COMPOUND_STMT = "compound_stmt";
  static final String BLOCK = "block";
  static final String SUBSCRIPTLIST = "subscriptlist";
  static final String SUBSCRIPT = "subscript_";
  static final String DICTORSETMAKER = "dictorsetmaker";
  static final String TESTLIST_COMP = "testlist_comp";
  static final String COMP_FOR = "comp_for";

  private PythonTypeKinds() {}

  /** Returns the node under {@code node} that is reached by following single children. */
  static SyntaxNode descend(final SyntaxNode node) {
    SyntaxNode current = node;
    while (current.children().size() == 1) {
      current = current.children().get(0);
    }
    return current;
  }

  /** Whether {@code node} is an identifier, as a token or as the {@code name} rule over one. */
  static boolean isName(final SyntaxNode node) {
    return TOKEN_NAME.equals(node.kind()) || PythonNames.NAME.equals(node.kind());
  }

  /** Whether {@code node} is a token whose text is {@code text}. */
  static boolean isToken(final SyntaxNode node, final String text) {
    return node.children().isEmpty() && text.equals(PythonSyntax.text(node));
  }
}
