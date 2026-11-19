package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Lowers {@code return}, {@code break} and {@code continue}, and the early exit of {@code ?}. Every
 * other expression is one step.
 */
final class RustJumpLowering {
  private static final String LABEL = "LIFETIME_OR_LABEL";

  private final ControlFlowBuilder flow;

  /** Creates the lowering of the jumps of one body. */
  RustJumpLowering(final ControlFlowBuilder flow) {
    this.flow = flow;
  }

  /** Lowers an expression that is a statement, a tail or an arm and holds no block structure. */
  void jumpOrStep(final SyntaxNode node) {
    final SyntaxNode first = node.children().get(0);
    flow.add(node, false);
    if (RustTrees.isToken(first, RustKinds.RETURN)) {
      flow.tries().returnFrom();
    } else if (RustTrees.isToken(first, RustKinds.BREAK)) {
      flow.jumps().breakTo(label(node));
    } else if (RustTrees.isToken(first, RustKinds.CONTINUE)) {
      flow.jumps().continueTo(label(node));
    } else {
      propagates(node);
    }
  }

  private static String label(final SyntaxNode node) {
    final SyntaxNode label = RustTrees.child(node, LABEL);
    return label == null ? null : RustTrees.text(label);
  }

  /** A {@code ?} in the step may leave the body, so the step also reaches the exit. */
  void propagates(final SyntaxNode node) {
    if (node != null && hasQuestion(node)) {
      flow.edge(flow.current(), ControlFlowBuilder.EXIT, FlowEdgeKind.NORMAL);
    }
  }

  private static boolean hasQuestion(final SyntaxNode node) {
    if ("closureExpression".equals(node.kind()) || "asyncBlockExpression".equals(node.kind())) {
      return false;
    }
    final List<SyntaxNode> parts = node.children();
    final boolean here =
        RustKinds.EXPRESSION.equals(node.kind())
            && !parts.isEmpty()
            && RustTrees.isToken(parts.get(parts.size() - 1), RustKinds.QUESTION);
    return here || parts.stream().anyMatch(RustJumpLowering::hasQuestion);
  }
}
