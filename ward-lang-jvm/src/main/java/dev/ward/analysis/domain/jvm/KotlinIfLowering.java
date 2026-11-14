package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;

/** Lowers an {@code if} of a Kotlin body: a condition, a branch for each arm, and a join. */
final class KotlinIfLowering {

  private static final String EXPRESSION = "expression";
  private static final String BODY = "controlStructureBody";
  private static final String ELSE = "else";

  private final ControlFlowBuilder flow;
  private final KotlinStatements statements;

  KotlinIfLowering(final ControlFlowBuilder flow, final KotlinStatements statements) {
    this.flow = flow;
    this.statements = statements;
  }

  void ifStatement(final SyntaxNode node) {
    final SyntaxNode condition = KotlinFlowSyntax.child(node, EXPRESSION);
    SyntaxNode thenBody = null;
    SyntaxNode elseBody = null;
    boolean seenElse = false;
    for (final SyntaxNode part : KotlinFlowSyntax.parts(node)) {
      if (ELSE.equals(KotlinFlowSyntax.tokenText(part))) {
        seenElse = true;
      } else if (BODY.equals(part.kind())) {
        if (seenElse) {
          elseBody = part;
        } else {
          thenBody = part;
        }
      }
    }
    final boolean hasElse = seenElse;
    final int thenBlock = flow.newBlock();
    final int after = flow.newBlock();
    final int elseBlock = hasElse ? flow.newBlock() : after;
    statements.expression(condition, KotlinFlowSyntax.mayThrow(condition));
    flow.branch(thenBlock, elseBlock);
    flow.moveTo(thenBlock);
    statements.controlBody(thenBody);
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    if (hasElse) {
      flow.moveTo(elseBlock);
      statements.controlBody(elseBody);
      flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    }
    flow.moveTo(after);
  }
}
