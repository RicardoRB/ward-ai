package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;

/** Adds the conditions of an {@code if}, {@code guard} or {@code while} to the graph. */
final class SwiftConditions {
  private final ControlFlowBuilder flow;

  /** Creates the lowering of the conditions of one body. */
  SwiftConditions(final ControlFlowBuilder flow) {
    this.flow = flow;
  }

  /**
   * Adds each condition of a list in turn to the current block; a false one goes to {@code
   * otherwise}, and the current block ends as the one where all of them held.
   *
   * @param list the condition list
   * @param otherwise the block that runs when a condition is false
   */
  void add(final SyntaxNode list, final int otherwise) {
    for (final SyntaxNode condition : SwiftFlowSyntax.children(list, "condition")) {
      SwiftFlowSyntax.step(flow, condition);
      final int pass = flow.newBlock();
      flow.branch(pass, otherwise);
      flow.moveTo(pass);
    }
  }
}
