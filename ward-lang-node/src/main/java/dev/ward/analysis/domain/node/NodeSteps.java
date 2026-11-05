package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;

/** Adds the statement nodes of a body, splitting the block at each suspension point. */
final class NodeSteps {

  private static final String AWAIT = "await";

  private NodeSteps() {}

  /**
   * Adds a statement or condition node. A suspension point, an {@code await}, ends its block with a
   * normal edge to the next one, so the code after it is a block of its own; no other edge is
   * added.
   *
   * @param flow the builder of the body
   * @param node the statement or condition node
   * @param mayThrow whether the node can throw
   */
  static void add(final ControlFlowBuilder flow, final SyntaxNode node, final boolean mayThrow) {
    flow.add(node, mayThrow);
    if (suspends(node)) {
      final int next = flow.newBlock();
      flow.edge(flow.current(), next, FlowEdgeKind.NORMAL);
      flow.moveTo(next);
    }
  }

  /** Reports whether a node contains an {@code await} outside any nested function. */
  static boolean suspends(final SyntaxNode node) {
    if (NodeFlowSyntax.isFunction(node)) {
      return false;
    }
    if (node.children().isEmpty()) {
      return AWAIT.equals(NodeFlowSyntax.text(node));
    }
    for (final SyntaxNode child : node.children()) {
      if (suspends(child)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the error for a statement this lowering does not handle. */
  static IllegalStateException unsupported(final SyntaxNode node) {
    return new IllegalStateException("unsupported statement " + node.kind());
  }
}
