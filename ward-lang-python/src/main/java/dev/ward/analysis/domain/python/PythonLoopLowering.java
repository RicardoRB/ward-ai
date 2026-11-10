package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.function.Consumer;

/** Lowers the {@code while} and {@code for} loops of a Python body, with their else clauses. */
final class PythonLoopLowering {

  private static final String NAMED_EXPR = "namedexpr_test";
  private static final String BLOCK = "block";
  private static final String ITERABLE = "testlist_star_expr";
  private static final String TARGET = "exprlist";
  private static final int FIRST_BLOCK = 0;
  private static final int ELSE_BLOCK = 1;

  private final ControlFlowBuilder flow;
  private final PythonAccesses accesses;
  private final Consumer<SyntaxNode> statements;

  /**
   * Creates the lowering of the loops of one body.
   *
   * @param flow the builder of the body
   * @param accesses the finder of the body, which plans the loop target
   * @param statements lowers the statements of a block of the body
   */
  PythonLoopLowering(
      final ControlFlowBuilder flow,
      final PythonAccesses accesses,
      final Consumer<SyntaxNode> statements) {
    this.flow = flow;
    this.accesses = accesses;
    this.statements = statements;
  }

  void whileStatement(final SyntaxNode node) {
    final SyntaxNode condition = PythonFlowSyntax.child(node, NAMED_EXPR);
    final List<SyntaxNode> blocks = PythonFlowSyntax.children(node, BLOCK);
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int after = flow.newBlock();
    final int otherwise = blocks.size() > ELSE_BLOCK ? flow.newBlock() : after;
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    flow.add(condition, PythonFlowSyntax.mayThrow(condition));
    if (PythonFlowSyntax.isAlwaysTrue(condition)) {
      flow.edge(header, body, FlowEdgeKind.NORMAL);
    } else {
      flow.branch(body, otherwise);
    }
    loop(body, after, header, blocks.get(FIRST_BLOCK));
    elseClause(blocks, otherwise, after);
  }

  void forStatement(final SyntaxNode node) {
    final SyntaxNode iterable = PythonFlowSyntax.child(node, ITERABLE);
    final SyntaxNode target = PythonFlowSyntax.child(node, TARGET);
    final List<SyntaxNode> blocks = PythonFlowSyntax.children(node, BLOCK);
    final boolean alwaysRuns = PythonLoops.alwaysRuns(iterable);
    flow.add(iterable, PythonFlowSyntax.mayThrow(iterable));
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int after = flow.newBlock();
    final int otherwise = blocks.size() > ELSE_BLOCK ? flow.newBlock() : after;
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    if (alwaysRuns) {
      flow.edge(header, body, FlowEdgeKind.NORMAL);
    } else {
      flow.branch(body, otherwise);
    }
    flow.moveTo(body);
    accesses.plan(target, true);
    flow.add(target, false);
    loop(body, after, header, blocks.get(FIRST_BLOCK));
    if (alwaysRuns) {
      // The loop leaves from its last body block, so the target is bound on every path out.
      flow.edge(flow.current(), otherwise, FlowEdgeKind.NORMAL);
    }
    elseClause(blocks, otherwise, after);
  }

  /**
   * Lowers the body of a loop, whose {@code break} leaves to {@code after}, past the else clause.
   */
  private void loop(final int body, final int after, final int header, final SyntaxNode block) {
    flow.moveTo(body);
    flow.jumps().pushLoop(null, after, header);
    statements.accept(block);
    flow.jumps().popFrame();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
  }

  /**
   * Lowers the {@code else} block of a loop, which runs only when the loop ends without a break.
   */
  private void elseClause(final List<SyntaxNode> blocks, final int otherwise, final int after) {
    if (blocks.size() > ELSE_BLOCK) {
      flow.moveTo(otherwise);
      statements.accept(blocks.get(ELSE_BLOCK));
      flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    }
    flow.moveTo(after);
  }
}
