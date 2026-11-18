package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Lowers the {@code while}, {@code repeat} and {@code for} loops of one Swift body. */
final class SwiftLoopLowering {
  private static final String CODE_BLOCK = "code_block";
  private static final String EXPRESSION = "expression";

  private final ControlFlowBuilder flow;
  private final SwiftConditions conditions;
  private final Consumer<SyntaxNode> statements;

  /**
   * Creates the lowering of the loops of one body.
   *
   * @param flow the builder of the body
   * @param conditions the lowering of conditions
   * @param statements lowers the statements of a block of the body
   */
  SwiftLoopLowering(
      final ControlFlowBuilder flow,
      final SwiftConditions conditions,
      final Consumer<SyntaxNode> statements
  ) {
    this.flow = flow;
    this.conditions = conditions;
    this.statements = statements;
  }

  /** Lowers a loop; {@code label} names it for {@code break} and {@code continue}, or is null. */
  void loop(final SyntaxNode node, final String label) {
    switch (node.kind()) {
      case "while_statement" -> whileStatement(node, label);
      case "repeat_while_statement" -> repeatStatement(node, label);
      default -> forStatement(node, label);
    }
  }

  private void whileStatement(final SyntaxNode node, final String label) {
    final int header = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    conditions.add(SwiftFlowSyntax.child(node, "condition_list"), after);
    body(SwiftFlowSyntax.child(node, CODE_BLOCK), label, after, header);
    flow.moveTo(after);
  }

  private void repeatStatement(final SyntaxNode node, final String label) {
    final int start = flow.newBlock();
    final int test = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), start, FlowEdgeKind.NORMAL);
    flow.moveTo(start);
    body(SwiftFlowSyntax.child(node, CODE_BLOCK), label, after, test);
    flow.moveTo(test);
    SwiftFlowSyntax.step(flow, SwiftFlowSyntax.child(node, EXPRESSION));
    flow.branch(start, after);
    flow.moveTo(after);
  }

  /**
   * The variable and the sequence are one step in the loop body, so the variable takes the taint of
   * the sequence.
   */
  private void forStatement(final SyntaxNode node, final String label) {
    final SyntaxNode pattern = SwiftFlowSyntax.child(node, "pattern");
    final SyntaxNode iterable = SwiftFlowSyntax.child(node, EXPRESSION);
    final int header = flow.newBlock();
    final int loopBody = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    flow.branch(loopBody, after);
    flow.moveTo(loopBody);
    SwiftFlowSyntax.step(
        flow,
        new SyntaxNode(
            "for_header",
            SwiftFlowSyntax.between(pattern.span(), iterable.span()),
            Map.of(),
            List.of(pattern, iterable)
        )
    );
    body(SwiftFlowSyntax.child(node, CODE_BLOCK), label, after, header);
    flow.moveTo(after);
  }

  /** Lowers a loop body: {@code break} leaves to {@code after}, {@code continue} to the test. */
  private void body(final SyntaxNode block, final String label, final int after, final int test) {
    flow.jumps().pushLoop(label, after, test);
    statements.accept(block);
    flow.jumps().popFrame();
    flow.edge(flow.current(), test, FlowEdgeKind.NORMAL);
  }
}
