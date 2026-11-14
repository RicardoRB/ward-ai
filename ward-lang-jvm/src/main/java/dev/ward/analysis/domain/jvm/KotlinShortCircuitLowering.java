package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Lowers {@code &&}, {@code ||} and {@code ?:}. The first operand always runs; each later operand
 * has a block of its own that the operand before it may skip. Every operand makes the value of the
 * whole, so a tainted operand taints it.
 */
final class KotlinShortCircuitLowering {

  private static final String DISJUNCTION = "disjunction";

  private final ControlFlowBuilder flow;
  private final KotlinStatements statements;

  KotlinShortCircuitLowering(final ControlFlowBuilder flow, final KotlinStatements statements) {
    this.flow = flow;
    this.statements = statements;
  }

  /**
   * Lowers the operands in order.
   *
   * @param node a conjunction, a disjunction or an elvis expression
   * @param result where the value goes, or null when it is not used
   */
  void lower(final SyntaxNode node, final KotlinResult result) {
    final List<SyntaxNode> operands =
        KotlinFlowSyntax.parts(node).stream().filter(part -> !part.children().isEmpty()).toList();
    final boolean stopsOnTrue = DISJUNCTION.equals(node.kind());
    final int join = flow.newBlock();
    for (int index = 0; index < operands.size(); index++) {
      statements.statement(operands.get(index), result);
      if (index < operands.size() - 1) {
        final int next = flow.newBlock();
        if (stopsOnTrue) {
          flow.branch(join, next);
        } else {
          flow.branch(next, join);
        }
        flow.moveTo(next);
      }
    }
    flow.edge(flow.current(), join, FlowEdgeKind.NORMAL);
    flow.moveTo(join);
  }
}
