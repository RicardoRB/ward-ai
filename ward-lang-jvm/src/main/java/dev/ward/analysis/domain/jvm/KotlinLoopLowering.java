package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;

/** Lowers the {@code while}, {@code do}/{@code while} and {@code for} loops of a Kotlin body. */
final class KotlinLoopLowering {

  private static final String EXPRESSION = "expression";
  private static final String BODY = "controlStructureBody";

  private final ControlFlowBuilder flow;
  private final KotlinStatements statements;

  KotlinLoopLowering(final ControlFlowBuilder flow, final KotlinStatements statements) {
    this.flow = flow;
    this.statements = statements;
  }

  void whileLoop(final SyntaxNode node, final String label) {
    final SyntaxNode condition = KotlinFlowSyntax.child(node, EXPRESSION);
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    flow.add(condition, KotlinFlowSyntax.mayThrow(condition));
    flow.branch(body, after);
    loopBody(label, KotlinFlowSyntax.child(node, BODY), body, after, header);
  }

  void doWhileLoop(final SyntaxNode node, final String label) {
    final SyntaxNode condition = KotlinFlowSyntax.child(node, EXPRESSION);
    final int body = flow.newBlock();
    final int check = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), body, FlowEdgeKind.NORMAL);
    flow.moveTo(body);
    flow.jumps().pushLoop(label, after, check);
    run(KotlinFlowSyntax.child(node, BODY));
    flow.jumps().popFrame();
    flow.edge(flow.current(), check, FlowEdgeKind.NORMAL);
    flow.moveTo(check);
    flow.add(condition, KotlinFlowSyntax.mayThrow(condition));
    flow.branch(body, after);
    flow.moveTo(after);
  }

  /**
   * A {@code for} loop. The loop variable and the iterable are one node, written {@code x in
   * items}, so the variable's write sees the names read in the iterable.
   */
  void forLoop(final SyntaxNode node, final String label) {
    final SyntaxNode binding = firstBinding(node);
    final SyntaxNode iterable = KotlinFlowSyntax.child(node, EXPRESSION);
    final SyntaxNode both =
        new SyntaxNode(
            "forBinding",
            KotlinFlowSyntax.between(binding.span(), iterable.span()),
            Map.of(),
            List.of(binding, iterable));
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    flow.branch(body, after);
    flow.moveTo(body);
    flow.add(both, KotlinFlowSyntax.mayThrow(iterable));
    loopBody(label, KotlinFlowSyntax.child(node, BODY), body, after, header);
  }

  private static SyntaxNode firstBinding(final SyntaxNode node) {
    final SyntaxNode single = KotlinFlowSyntax.child(node, "variableDeclaration");
    return single == null ? KotlinFlowSyntax.child(node, "multiVariableDeclaration") : single;
  }

  private void loopBody(
      final String label,
      final SyntaxNode body,
      final int bodyBlock,
      final int after,
      final int continueBlock) {
    flow.jumps().pushLoop(label, after, continueBlock);
    flow.moveTo(bodyBlock);
    run(body);
    flow.jumps().popFrame();
    flow.edge(flow.current(), continueBlock, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }

  private void run(final SyntaxNode body) {
    if (body != null) {
      statements.controlBody(body);
    }
  }
}
