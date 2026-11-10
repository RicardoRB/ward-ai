package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** The statement lowering that the construct lowerings call back into for nested statements. */
interface NodeStatements {

  /**
   * Lowers one statement.
   *
   * @param node the statement node
   * @param label the label written on the statement, or null
   */
  void statement(SyntaxNode node, String label);

  /**
   * Lowers the statements of a block or of a body.
   *
   * @param block the block node
   */
  void blockStatements(SyntaxNode block);
}
