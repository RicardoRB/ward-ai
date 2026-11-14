package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;

/** Picks the lowering of a Kotlin control construct: a branch, a handler, a loop or an operator. */
final class KotlinConstructLowering {

  private final KotlinValueLowering values;
  private final KotlinLoopLowering loops;
  private final KotlinHandlerLowering handlers;
  private final KotlinIfLowering branches;
  private final KotlinShortCircuitLowering shorts;

  KotlinConstructLowering(
      final ControlFlowBuilder flow,
      final KotlinValueLowering values,
      final KotlinStatements statements) {
    this.values = values;
    this.loops = new KotlinLoopLowering(flow, statements);
    this.handlers = new KotlinHandlerLowering(flow, statements);
    this.branches = new KotlinIfLowering(flow, statements);
    this.shorts = new KotlinShortCircuitLowering(flow, statements);
  }

  /**
   * Lowers a construct, or an expression that is none.
   *
   * @param construct the construct, with its wrappers removed
   * @param label the label written before a loop, or null
   * @param result where the value goes, or null when it is not used
   */
  void lower(final SyntaxNode construct, final String label, final KotlinResult result) {
    switch (construct.kind()) {
      case "ifExpression" -> branches.ifStatement(construct, result);
      case "whenExpression" -> handlers.whenStatement(construct, result);
      case "tryExpression" -> handlers.tryStatement(construct, result);
      case "conjunction", "disjunction", "elvisExpression" -> shorts.lower(construct, result);
      default -> loopOrExpression(construct, label, result);
    }
  }

  private void loopOrExpression(
      final SyntaxNode construct, final String label, final KotlinResult result) {
    switch (construct.kind()) {
      case "whileExpression" -> loops.whileLoop(construct, label);
      case "doWhileExpression" -> loops.doWhileLoop(construct, label);
      case "forExpression" -> loops.forLoop(construct, label);
      default -> values.expression(construct, KotlinFlowSyntax.mayThrow(construct), result);
    }
  }
}
