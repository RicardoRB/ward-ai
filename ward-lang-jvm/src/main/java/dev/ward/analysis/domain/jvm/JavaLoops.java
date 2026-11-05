package dev.ward.analysis.domain.jvm;

import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.firstOf;
import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.mayThrow;
import static dev.ward.analysis.domain.jvm.JavaFlowSyntax.parts;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Lowers the {@code while}, {@code do} and {@code for} loops of a Java body. */
final class JavaLoops {

  private static final int DO_BODY_INDEX = 1;
  private static final int CONDITION_INDEX = 2;
  private static final int BODY_INDEX = 4;
  private static final int DO_CONDITION_INDEX = 4;

  private final ControlFlowBuilder flow;
  private final JavaBranches branches;
  private final JavaStatements statements;

  JavaLoops(
      final ControlFlowBuilder flow, final JavaBranches branches, final JavaStatements statements) {
    this.flow = flow;
    this.branches = branches;
    this.statements = statements;
  }

  boolean whileStatement(final List<SyntaxNode> parts, final String label) {
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    branches.condition(parts.get(CONDITION_INDEX), body, after);
    loopBody(label, parts.get(BODY_INDEX), body, after, header);
    return true;
  }

  boolean doStatement(final List<SyntaxNode> parts, final String label) {
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

  boolean forStatement(final List<SyntaxNode> parts, final String label) {
    final SyntaxNode control = parts.get(CONDITION_INDEX);
    final SyntaxNode each = firstOf(parts(control), "enhancedForControl");
    if (each == null) {
      forClassic(control, label, parts.get(BODY_INDEX));
    } else {
      forEach(each, label, parts.get(BODY_INDEX));
    }
    return true;
  }

  private void forEach(final SyntaxNode each, final String label, final SyntaxNode body) {
    final List<SyntaxNode> parts = parts(each);
    final SyntaxNode iterable = parts.get(parts.size() - 1);
    flow.add(iterable, mayThrow(iterable));
    final int header = flow.newBlock();
    final int bodyBlock = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    flow.branch(bodyBlock, after);
    flow.moveTo(bodyBlock);
    final SyntaxNode binding = firstOf(parts, "variableDeclaratorId");
    if (binding != null) {
      flow.add(binding, false);
    }
    loopBody(label, body, bodyBlock, after, header);
  }

  private void forClassic(final SyntaxNode control, final String label, final SyntaxNode body) {
    final SyntaxNode init = firstOf(parts(control), "forInit");
    final SyntaxNode condition = firstOf(parts(control), "expression");
    final SyntaxNode update = firstOf(parts(control), "expressionList");
    if (init != null) {
      flow.add(init, mayThrow(init));
    }
    final int header = flow.newBlock();
    final int bodyBlock = flow.newBlock();
    final int step = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    if (condition == null) {
      flow.edge(header, bodyBlock, FlowEdgeKind.NORMAL);
    } else {
      branches.condition(condition, bodyBlock, after);
    }
    loopBody(label, body, bodyBlock, after, step);
    flow.moveTo(step);
    if (update != null) {
      flow.add(update, mayThrow(update));
    }
    flow.edge(step, header, FlowEdgeKind.NORMAL);
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
