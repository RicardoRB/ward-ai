package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Lowers the expressions of a Java statement that split the graph: a logical operator on the right
 * of a condition, and a conditional operator, each with its own blocks.
 */
final class JavaBranches {

  private static final String AND = "&&";
  private static final int RIGHT_INDEX = 2;
  private static final int TERNARY_ELSE_INDEX = 4;

  private final ControlFlowBuilder flow;

  JavaBranches(final ControlFlowBuilder flow) {
    this.flow = flow;
  }

  /**
   * A statement with one expression. A logical or conditional operator at its top level, or on the
   * right of an assignment, splits the graph; the statement node then follows the split, when the
   * statement is an assignment or a return.
   */
  void valueStatement(
      final SyntaxNode statement, final SyntaxNode expression, final boolean wholeStatement) {
    final SyntaxNode value = JavaFlowSyntax.unwrap(expression);
    final SyntaxNode split = JavaFlowSyntax.assignedValue(value);
    if (!JavaFlowSyntax.isLogical(split) && !JavaFlowSyntax.isTernary(split)) {
      flow.add(statement, JavaFlowSyntax.mayThrow(statement));
      return;
    }
    emitValue(split);
    if (wholeStatement || !split.equals(value)) {
      flow.add(statement, JavaFlowSyntax.mayThrow(statement));
    }
  }

  void emitValue(final SyntaxNode expression) {
    final SyntaxNode value = JavaFlowSyntax.unwrap(expression);
    if (JavaFlowSyntax.isLogical(value)) {
      logicalValue(value);
    } else if (JavaFlowSyntax.isTernary(value)) {
      ternaryValue(value);
    } else {
      flow.add(value, JavaFlowSyntax.mayThrow(value));
    }
  }

  void logicalValue(final SyntaxNode value) {
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(value);
    final int rhs = flow.newBlock();
    final int join = flow.newBlock();
    emitValue(parts.get(0));
    if (AND.equals(JavaFlowSyntax.text(parts.get(1)))) {
      flow.branch(rhs, join);
    } else {
      flow.branch(join, rhs);
    }
    flow.moveTo(rhs);
    emitValue(parts.get(RIGHT_INDEX));
    flow.edge(flow.current(), join, FlowEdgeKind.NORMAL);
    flow.moveTo(join);
  }

  void ternaryValue(final SyntaxNode value) {
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(value);
    final int thenBlock = flow.newBlock();
    final int elseBlock = flow.newBlock();
    final int join = flow.newBlock();
    condition(parts.get(0), thenBlock, elseBlock);
    flow.moveTo(thenBlock);
    emitValue(parts.get(RIGHT_INDEX));
    flow.edge(flow.current(), join, FlowEdgeKind.NORMAL);
    flow.moveTo(elseBlock);
    emitValue(parts.get(TERNARY_ELSE_INDEX));
    flow.edge(flow.current(), join, FlowEdgeKind.NORMAL);
    flow.moveTo(join);
  }

  /** Branches from the current block on a condition, short-circuiting {@code &&} and {@code ||}. */
  void condition(final SyntaxNode expression, final int ifTrue, final int ifFalse) {
    final SyntaxNode value = JavaFlowSyntax.unwrap(expression);
    if (!JavaFlowSyntax.isLogical(value)) {
      flow.add(value, JavaFlowSyntax.mayThrow(value));
      flow.branch(ifTrue, ifFalse);
      return;
    }
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(value);
    final int rhs = flow.newBlock();
    if (AND.equals(JavaFlowSyntax.text(parts.get(1)))) {
      condition(parts.get(0), rhs, ifFalse);
    } else {
      condition(parts.get(0), ifTrue, rhs);
    }
    flow.moveTo(rhs);
    condition(parts.get(2), ifTrue, ifFalse);
  }
}
