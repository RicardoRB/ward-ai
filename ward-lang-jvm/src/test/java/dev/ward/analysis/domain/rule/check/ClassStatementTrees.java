package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Builds the statements and calls the class-level design check tests read. */
class ClassStatementTrees extends ClassMemberTrees {

  static final String LOOKUP = "get";
  static final String USE = "use";
  static final String COMPUTE = "compute";
  static final String RUN = "run";
  static final String ACCESS_PREFIX = "f";
  static final int DECISIONS_FOR_WMC_47 = 46;
  static final int ACCESSES = 6;

  protected ClassStatementTrees() {
    super();
  }

  /** A statement {@code receiver.name}, with the receiver at the shared test position. */
  SyntaxNode access(final String receiver, final String name) {
    return node(
        EXPRESSION,
        LINE,
        COLUMN,
        node(EXPRESSION, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, receiver, LINE, COLUMN)),
        leaf(DOT_KIND, DOT, LINE, COLUMN),
        ident(name));
  }

  /** A statement {@code receiver.name()}, with the receiver at the shared test position. */
  SyntaxNode call(final String receiver, final String name) {
    return node(
        EXPRESSION,
        LINE,
        COLUMN,
        node(EXPRESSION, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, receiver, LINE, COLUMN)),
        leaf(DOT_KIND, DOT, LINE, COLUMN),
        node(METHOD_CALL, LINE, COLUMN, ident(name)));
  }

  /** A statement {@code receiver.get().use()}, with the inner call on the receiver. */
  SyntaxNode chainOn(final String receiver) {
    return node(
        EXPRESSION,
        LINE,
        COLUMN,
        call(receiver, LOOKUP),
        leaf(DOT_KIND, DOT, LINE, COLUMN),
        node(METHOD_CALL, LINE, COLUMN, ident(USE)));
  }

  SyntaxNode nestedChain() {
    return node(
        EXPRESSION,
        LINE,
        COLUMN,
        chainOn(FOREIGN),
        leaf(DOT_KIND, DOT, LINE, COLUMN),
        node(METHOD_CALL, LINE, COLUMN, ident("again")));
  }

  SyntaxNode bareExpression() {
    return node(EXPRESSION, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, RUN, LINE, COLUMN));
  }

  /** A method reading six fields of a foreign object, and touching the shared field when asked. */
  SyntaxNode accessesMethod(final boolean usesField) {
    final List<SyntaxNode> body = new ArrayList<>();
    for (int index = 0; index < ACCESSES; index++) {
      body.add(access(FOREIGN, ACCESS_PREFIX + index));
    }
    if (usesField) {
      body.add(ident(FIELD_NAME));
    }
    return member(method("read", 0, 0, body.toArray(SyntaxNode[]::new)), PUBLIC);
  }

  /** A method with the decisions that make its complexity 47, touching the field when asked. */
  SyntaxNode branchesMethod(final boolean usesField) {
    final List<SyntaxNode> body = new ArrayList<>();
    if (usesField) {
      body.add(ident(FIELD_NAME));
    }
    return member(
        method("branches", 0, DECISIONS_FOR_WMC_47, body.toArray(SyntaxNode[]::new)), PUBLIC);
  }

  SyntaxNode usingMethod(final String name) {
    return member(method(name, 0, 0, ident(FIELD_NAME)), PUBLIC);
  }
}
