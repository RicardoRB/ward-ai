package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.mayThrow;
import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.text;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Lowers the statements that leave a block: {@code return}, {@code throw}, {@code break}, {@code
 * continue} and {@code yield}, and the plain statements that never leave one.
 */
final class JavaJumps {

  private static final int VALUE_INDEX = 1;
  private static final int LABEL_INDEX = 1;
  private static final int MIN_WITH_OPERAND = 2;

  private final ControlFlowBuilder flow;
  private final JavaBranches branches;

  JavaJumps(final ControlFlowBuilder flow, final JavaBranches branches) {
    this.flow = flow;
    this.branches = branches;
  }

  boolean returnStatement(final SyntaxNode node, final List<SyntaxNode> parts) {
    if (parts.size() > MIN_WITH_OPERAND) {
      branches.valueStatement(node, parts.get(VALUE_INDEX), true);
    } else {
      flow.add(node, false);
    }
    flow.tries().returnFrom();
    return true;
  }

  boolean throwStatement(final SyntaxNode node) {
    flow.add(node, true);
    flow.tries().throwOut();
    return true;
  }

  boolean breakStatement(final SyntaxNode node, final List<SyntaxNode> parts) {
    flow.add(node, false);
    flow.jumps().breakTo(jumpLabel(parts));
    return true;
  }

  boolean continueStatement(final SyntaxNode node, final List<SyntaxNode> parts) {
    flow.add(node, false);
    flow.jumps().continueTo(jumpLabel(parts));
    return true;
  }

  boolean yieldStatement(final SyntaxNode node) {
    flow.add(node, mayThrow(node));
    flow.jumps().yieldTo();
    return true;
  }

  boolean plainStatement(final SyntaxNode node) {
    flow.add(node, mayThrow(node));
    return true;
  }

  private static String jumpLabel(final List<SyntaxNode> parts) {
    return parts.size() > MIN_WITH_OPERAND ? text(parts.get(LABEL_INDEX)) : null;
  }
}
