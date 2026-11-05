package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Lowers the {@code do}, {@code while} and {@code for} loops of a TypeScript or JavaScript body.
 */
final class NodeLoops {

  private static final int CONDITION_INDEX = 2;
  private static final int BODY_INDEX = 4;
  private static final int DO_BODY_INDEX = 1;
  private static final int DO_CONDITION_INDEX = 4;
  private static final int HEADER_OPEN_INDEX = 1;
  private static final int CLOSE_BEFORE_BODY = 2;

  private final ControlFlowBuilder flow;
  private final NodeBranches branches;
  private final NodeStatements statements;

  NodeLoops(
      final ControlFlowBuilder flow, final NodeBranches branches, final NodeStatements statements) {
    this.flow = flow;
    this.branches = branches;
    this.statements = statements;
  }

  /** Lowers an iteration statement, dispatching on its first keyword. */
  boolean iteration(final List<SyntaxNode> parts, final String label) {
    return switch (NodeFlowSyntax.text(parts.get(0))) {
      case "do" -> doStatement(parts, label);
      case "while" -> whileStatement(parts, label);
      case "for" -> forStatement(parts, label);
      default -> false;
    };
  }

  private boolean whileStatement(final List<SyntaxNode> parts, final String label) {
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    branches.condition(parts.get(CONDITION_INDEX), body, after);
    loopBody(label, parts.get(BODY_INDEX), body, after, header);
    return true;
  }

  private boolean doStatement(final List<SyntaxNode> parts, final String label) {
    final int body = flow.newBlock();
    final int check = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), body, FlowEdgeKind.NORMAL);
    flow.moveTo(body);
    flow.jumps().pushLoop(label, after, check);
    statements.statement(parts.get(DO_BODY_INDEX), null);
    flow.jumps().popFrame();
    flow.edge(flow.current(), check, FlowEdgeKind.NORMAL);
    flow.moveTo(check);
    branches.condition(parts.get(DO_CONDITION_INDEX), body, after);
    flow.moveTo(after);
    return true;
  }

  /**
   * Lowers {@code for (;;)}, {@code for (x in xs)} and {@code for (x of xs)}, with or without
   * await.
   */
  private boolean forStatement(final List<SyntaxNode> parts, final String label) {
    final int open = NodeForHeaders.indexOfToken(parts, "(");
    final SyntaxNode body = parts.get(parts.size() - 1);
    final List<SyntaxNode> header =
        parts.subList(open + HEADER_OPEN_INDEX, parts.size() - CLOSE_BEFORE_BODY);
    if (NodeForHeaders.isForEach(header)) {
      forEach(header, label, body);
    } else {
      forClassic(header, label, body);
    }
    return true;
  }

  private void forEach(final List<SyntaxNode> header, final String label, final SyntaxNode body) {
    final List<SyntaxNode> sequences = NodeFlowSyntax.allOf(header, "expressionSequence");
    final SyntaxNode iterable = sequences.get(sequences.size() - 1);
    NodeSteps.add(flow, iterable, NodeExpressions.mayThrow(iterable));
    final int headerBlock = flow.newBlock();
    final int bodyBlock = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), headerBlock, FlowEdgeKind.NORMAL);
    flow.moveTo(headerBlock);
    flow.branch(bodyBlock, after);
    flow.moveTo(bodyBlock);
    NodeSteps.add(flow, NodeForHeaders.bindingOf(header), false);
    loopBody(label, body, bodyBlock, after, headerBlock);
  }

  private void forClassic(
      final List<SyntaxNode> header, final String label, final SyntaxNode body) {
    final List<List<SyntaxNode>> clauses = NodeForHeaders.splitOnSemicolons(header);
    if (!clauses.get(0).isEmpty()) {
      final SyntaxNode init = NodeForHeaders.last(clauses.get(0));
      NodeSteps.add(flow, init, NodeExpressions.mayThrow(init));
    }
    final int headerBlock = flow.newBlock();
    final int bodyBlock = flow.newBlock();
    final int step = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), headerBlock, FlowEdgeKind.NORMAL);
    flow.moveTo(headerBlock);
    if (clauses.get(1).isEmpty()) {
      flow.edge(headerBlock, bodyBlock, FlowEdgeKind.NORMAL);
    } else {
      branches.condition(NodeForHeaders.last(clauses.get(1)), bodyBlock, after);
    }
    loopBody(label, body, bodyBlock, after, step);
    flow.moveTo(step);
    if (!clauses.get(2).isEmpty()) {
      final SyntaxNode update = NodeForHeaders.last(clauses.get(2));
      NodeSteps.add(flow, update, NodeExpressions.mayThrow(update));
    }
    flow.edge(step, headerBlock, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }

  /** Lowers a loop body, with its back edge to the block that continues the loop. */
  private void loopBody(
      final String label,
      final SyntaxNode body,
      final int bodyBlock,
      final int after,
      final int continueBlock) {
    flow.jumps().pushLoop(label, after, continueBlock);
    flow.moveTo(bodyBlock);
    statements.statement(body, null);
    flow.jumps().popFrame();
    flow.edge(flow.current(), continueBlock, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }
}
