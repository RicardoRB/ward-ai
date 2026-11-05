package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Lowers the statements that leave a TypeScript or JavaScript block: return, throw, break,
 * continue.
 */
final class NodeJumps {

  private static final String IDENTIFIER = "identifier";
  private static final String EXPRESSION_SEQUENCE = "expressionSequence";
  private static final int VALUE_INDEX = 1;

  private final ControlFlowBuilder flow;
  private final NodeBranches branches;

  NodeJumps(final ControlFlowBuilder flow, final NodeBranches branches) {
    this.flow = flow;
    this.branches = branches;
  }

  boolean returnStatement(final SyntaxNode part) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(part);
    if (parts.size() > VALUE_INDEX && EXPRESSION_SEQUENCE.equals(parts.get(VALUE_INDEX).kind())) {
      branches.valueStatement(part, parts.get(VALUE_INDEX), true);
    } else {
      NodeSteps.add(flow, part, false);
    }
    flow.tries().returnFrom();
    return true;
  }

  boolean throwStatement(final SyntaxNode part) {
    NodeSteps.add(flow, part, true);
    flow.tries().throwOut();
    return true;
  }

  boolean breakStatement(final SyntaxNode part) {
    NodeSteps.add(flow, part, false);
    flow.jumps().breakTo(jumpLabel(part));
    return true;
  }

  boolean continueStatement(final SyntaxNode part) {
    NodeSteps.add(flow, part, false);
    flow.jumps().continueTo(jumpLabel(part));
    return true;
  }

  private static String jumpLabel(final SyntaxNode part) {
    final SyntaxNode label = NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(part), IDENTIFIER);
    return label == null ? null : NodeFlowSyntax.text(label);
  }
}
