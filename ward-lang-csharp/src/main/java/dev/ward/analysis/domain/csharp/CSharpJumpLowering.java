package dev.ward.analysis.domain.csharp;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Map;
import java.util.function.Consumer;

/** Lowers {@code return}, {@code throw}, {@code break}, {@code continue} and {@code yield}. */
final class CSharpJumpLowering {
  private final ControlFlowBuilder flow;

  CSharpJumpLowering(final ControlFlowBuilder flow) {
    this.flow = flow;
  }

  void register(final Map<String, Consumer<SyntaxNode>> handlers) {
    handlers.put("return_statement", this::returnStatement);
    handlers.put("throw_statement", this::throwStatement);
    handlers.put("break_statement", this::breakStatement);
    handlers.put("continue_statement", this::continueStatement);
    handlers.put("yield_statement", this::yieldStatement);
  }

  private void returnStatement(final SyntaxNode node) {
    flow.add(node, true);
    flow.tries().returnFrom();
  }

  private void throwStatement(final SyntaxNode node) {
    flow.add(node, true);
    flow.tries().throwOut();
  }

  private void breakStatement(final SyntaxNode node) {
    flow.add(node, false);
    flow.jumps().breakTo(null);
  }

  private void continueStatement(final SyntaxNode node) {
    flow.add(node, false);
    flow.jumps().continueTo(null);
  }

  private void yieldStatement(final SyntaxNode node) {
    if (CSharpSyntax.hasToken(node, "break")) {
      flow.add(node, false);
      flow.tries().returnFrom();
    } else {
      flow.add(node, true);
    }
  }
}
