package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;

/**
 * A place a nullness rule reports.
 *
 * @param node the node to report at
 * @param value what fills the {@code {value}} placeholder of the message
 */
record NullHit(SyntaxNode node, String value) {

  /** Returns the hit at {@code node}; a loop calls this, not the constructor. */
  static NullHit at(final SyntaxNode node, final String value) {
    return new NullHit(node, value);
  }
}
