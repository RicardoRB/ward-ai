package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Lowers the {@code while}, {@code do}, {@code for} and {@code foreach} loops of one C# body. */
final class CSharpLoopLowering {
  private static final String EMBEDDED = "embedded_statement";
  private static final String BLOCK = "block";

  private final ControlFlowBuilder flow;
  private final Consumer<SyntaxNode> statements;

  CSharpLoopLowering(final ControlFlowBuilder flow, final Consumer<SyntaxNode> statements) {
    this.flow = flow;
    this.statements = statements;
  }

  void register(final Map<String, Consumer<SyntaxNode>> handlers) {
    handlers.put("while_statement", this::whileStatement);
    handlers.put("do_statement", this::doStatement);
    handlers.put("for_statement", this::forStatement);
    handlers.put("foreach_statement", this::foreachStatement);
  }

  /** Adds the condition of an {@code if}, {@code while} or {@code do} as a step. */
  static void condition(final ControlFlowBuilder flow, final SyntaxNode node) {
    CSharpSyntax.child(node, "boolean_expression").ifPresent(found -> flow.add(found, true));
  }

  /** Returns the embedded statements and blocks of a statement, in source order. */
  static List<SyntaxNode> bodies(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> EMBEDDED.equals(child.kind()) || BLOCK.equals(child.kind()))
        .toList();
  }

  private void whileStatement(final SyntaxNode node) {
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    condition(flow, node);
    flow.branch(body, after);
    flow.moveTo(body);
    loopBody(node, after, header);
    flow.moveTo(after);
  }

  private void doStatement(final SyntaxNode node) {
    final int start = flow.newBlock();
    final int test = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), start, FlowEdgeKind.NORMAL);
    flow.moveTo(start);
    loopBody(node, after, test);
    flow.moveTo(test);
    condition(flow, node);
    flow.branch(start, after);
    flow.moveTo(after);
  }

  private void forStatement(final SyntaxNode node) {
    CSharpSyntax.child(node, "for_initializer").ifPresent(found -> flow.add(found, true));
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int iterate = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    CSharpSyntax.child(node, "for_condition").ifPresent(found -> flow.add(found, true));
    flow.branch(body, after);
    flow.moveTo(body);
    loopBody(node, after, iterate);
    flow.moveTo(iterate);
    CSharpSyntax.child(node, "for_iterator").ifPresent(found -> flow.add(found, true));
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }

  private void foreachStatement(final SyntaxNode node) {
    final int header = flow.newBlock();
    final int body = flow.newBlock();
    final int after = flow.newBlock();
    CSharpSyntax.child(node, "expression").ifPresent(found -> flow.add(found, true));
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    flow.branch(body, after);
    flow.moveTo(body);
    loopBody(node, after, header);
    flow.moveTo(after);
  }

  /** Lowers a loop body: {@code break} leaves to {@code after}, {@code continue} to the test. */
  private void loopBody(final SyntaxNode loop, final int after, final int test) {
    flow.jumps().pushLoop(null, after, test);
    bodies(loop).forEach(statements);
    flow.jumps().popFrame();
    flow.edge(flow.current(), test, FlowEdgeKind.NORMAL);
  }
}
