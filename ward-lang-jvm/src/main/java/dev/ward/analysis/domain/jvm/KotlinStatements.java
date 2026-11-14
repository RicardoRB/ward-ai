package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** What the Kotlin lowerings of loops, handlers and branches need from the body that owns them. */
interface KotlinStatements {

  /** Lowers a statement, a block-level expression or an expression used as a statement. */
  void statement(SyntaxNode node);

  /** Lowers the body of a branch or loop: a block, or one expression. */
  void controlBody(SyntaxNode body);

  /** Lowers the statements of a block. */
  void block(SyntaxNode block);
}
