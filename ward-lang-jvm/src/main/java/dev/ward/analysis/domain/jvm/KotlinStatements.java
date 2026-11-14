package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;

/** What the Kotlin lowerings of loops, handlers and branches need from the body that owns them. */
interface KotlinStatements {

  /** Lowers a statement, a block-level expression or an expression used as a statement. */
  void statement(SyntaxNode node);

  /**
   * Lowers a statement that may make the value of the part it is in, such as the last statement of
   * a lambda.
   *
   * @param result where the value goes, or null when the value is not used
   */
  void statement(SyntaxNode node, KotlinResult result);

  /** Lowers the statements of a lambda; the last one makes the value. */
  void tail(SyntaxNode statements, KotlinResult result);

  /** Lowers an expression that is a condition, a subject or a bound. */
  void expression(SyntaxNode node, boolean mayThrow);

  /** Lowers the body of a branch or loop: a block, or one expression. */
  void controlBody(SyntaxNode body);

  /** Lowers the statements of a block. */
  void block(SyntaxNode block);
}
