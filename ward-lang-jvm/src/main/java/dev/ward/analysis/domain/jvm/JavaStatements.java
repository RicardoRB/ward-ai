package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** The statement lowering that the construct lowerings call back into for nested statements. */
interface JavaStatements {

  /**
   * Lowers one statement.
   *
   * @param node the statement node
   * @param label the label written on the statement, or null
   */
  void statement(SyntaxNode node, String label);

  /**
   * Lowers one block statement of a block.
   *
   * @param node the block statement node
   */
  void blockStatement(SyntaxNode node);

  /**
   * Lowers the block statements of a block.
   *
   * @param block the block node
   */
  void blockStatements(SyntaxNode block);
}
