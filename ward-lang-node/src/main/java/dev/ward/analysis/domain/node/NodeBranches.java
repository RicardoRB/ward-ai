package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * Lowers the expressions of a TypeScript or JavaScript statement that split the graph: a logical
 * operator, a conditional operator, or the right-hand side of an assignment that holds either.
 */
final class NodeBranches {

  private static final String AND = "&&";
  private static final int RIGHT_INDEX = 2;
  private static final int DECLARATION_PARTS = 3;
  private static final int TERNARY_ELSE_INDEX = 4;

  private final ControlFlowBuilder flow;
  private final NodeAccesses accesses;

  NodeBranches(final ControlFlowBuilder flow, final NodeAccesses accesses) {
    this.flow = flow;
    this.accesses = accesses;
  }

  /**
   * A statement with one expression. A logical or conditional operator at its top level, or on the
   * right of an assignment, splits the graph; the statement node then follows the split when the
   * statement is an assignment or a return.
   */
  void valueStatement(
      final SyntaxNode statement, final SyntaxNode expression, final boolean wholeStatement) {
    final SyntaxNode value = NodeFlowSyntax.unwrap(expression);
    final SyntaxNode split = NodeExpressions.assignedValue(value);
    if (!NodeExpressions.isLogical(split) && !NodeExpressions.isTernary(split)) {
      NodeSteps.add(flow, statement, NodeExpressions.mayThrow(statement));
      return;
    }
    emitValue(split);
    accesses.skip(split);
    if (wholeStatement || !split.equals(value)) {
      NodeSteps.add(flow, statement, NodeExpressions.mayThrow(statement));
    }
  }

  /** Lowers an expression, splitting it at its top-level logical or conditional operator. */
  void emitValue(final SyntaxNode expression) {
    final SyntaxNode value = NodeFlowSyntax.unwrap(expression);
    if (NodeExpressions.isLogical(value)) {
      logicalValue(value);
    } else if (NodeExpressions.isTernary(value)) {
      ternaryValue(value);
    } else {
      NodeSteps.add(flow, value, NodeExpressions.mayThrow(value));
    }
  }

  /**
   * A variable statement. A logical or conditional operator on its right splits the graph, as it
   * does for an assignment; {@code x = a || b} parses as a declaration in both grammars.
   */
  boolean variableStatement(final SyntaxNode part) {
    final SyntaxNode assigned = assignedExpression(part);
    if (assigned != null) {
      final SyntaxNode value = NodeFlowSyntax.unwrap(assigned);
      if (NodeExpressions.isLogical(value) || NodeExpressions.isTernary(value)) {
        emitValue(value);
        accesses.skip(value);
      }
    }
    NodeSteps.add(flow, part, NodeExpressions.mayThrow(part));
    return true;
  }

  /** Branches from the current block on a condition, short-circuiting the logical operators. */
  void condition(final SyntaxNode expression, final int ifTrue, final int ifFalse) {
    final SyntaxNode value = NodeFlowSyntax.unwrap(expression);
    if (!NodeExpressions.isLogical(value)) {
      NodeSteps.add(flow, value, NodeExpressions.mayThrow(value));
      flow.branch(ifTrue, ifFalse);
      return;
    }
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(value);
    final int rhs = flow.newBlock();
    if (AND.equals(NodeFlowSyntax.text(parts.get(1)))) {
      condition(parts.get(0), rhs, ifFalse);
    } else {
      condition(parts.get(0), ifTrue, rhs);
    }
    flow.moveTo(rhs);
    condition(parts.get(RIGHT_INDEX), ifTrue, ifFalse);
  }

  private void logicalValue(final SyntaxNode value) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(value);
    final int rhs = flow.newBlock();
    final int join = flow.newBlock();
    emitValue(parts.get(0));
    if (AND.equals(NodeFlowSyntax.text(parts.get(1)))) {
      flow.branch(rhs, join);
    } else {
      flow.branch(join, rhs);
    }
    flow.moveTo(rhs);
    emitValue(parts.get(RIGHT_INDEX));
    flow.edge(flow.current(), join, FlowEdgeKind.NORMAL);
    flow.moveTo(join);
  }

  private void ternaryValue(final SyntaxNode value) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(value);
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

  /** Returns the expression the first declarator of a variable statement assigns, or null. */
  private static SyntaxNode assignedExpression(final SyntaxNode statement) {
    final SyntaxNode initializer =
        NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(statement), "initializer");
    if (initializer != null) {
      final List<SyntaxNode> parts = NodeFlowSyntax.parts(initializer);
      return parts.get(parts.size() - 1);
    }
    final SyntaxNode list =
        NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(statement), "variableDeclarationList");
    final SyntaxNode declaration =
        list == null
            ? null
            : NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(list), "variableDeclaration");
    if (declaration == null) {
      return null;
    }
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(declaration);
    final boolean assigns =
        parts.size() >= DECLARATION_PARTS
            && "=".equals(NodeFlowSyntax.text(parts.get(parts.size() - 2)));
    return assigns ? parts.get(parts.size() - 1) : null;
  }
}
