package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** Builds the chains {@code receiver.lookup().use()} of the law-of-Demeter tests. */
abstract class LawOfDemeterTrees extends ClassStatementTrees {
  static final String ANOTHER_METHOD = "again";

  SyntaxNode chainOf(final String receiver) {
    return chainOf(receiver, LOOKUP);
  }

  /** {@code receiver.lookup().use()}; the inner call starts one column after the receiver. */
  SyntaxNode chainOf(final String receiver, final String lookup) {
    return node(
        EXPRESSION,
        LINE,
        COLUMN,
        node(
            EXPRESSION,
            LINE,
            COLUMN + 1,
            node(EXPRESSION, LINE, COLUMN, leaf(TOKEN_IDENTIFIER, receiver, LINE, COLUMN)),
            leaf(DOT_KIND, DOT, LINE, COLUMN),
            node(METHOD_CALL, LINE, COLUMN, ident(lookup))
        ),
        leaf(DOT_KIND, DOT, LINE, COLUMN),
        node(METHOD_CALL, LINE, COLUMN, ident(USE))
    );
  }

  SyntaxNode nestedChainOf() {
    return node(
        EXPRESSION,
        LINE,
        COLUMN,
        chainOf(FOREIGN),
        leaf(DOT_KIND, DOT, LINE, COLUMN),
        node(METHOD_CALL, LINE, COLUMN, ident(ANOTHER_METHOD))
    );
  }
}
