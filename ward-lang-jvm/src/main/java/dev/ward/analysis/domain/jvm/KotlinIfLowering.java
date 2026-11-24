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
  private final KotlinAccesses accesses;
  private final KotlinAllowListGuards guards;

  KotlinIfLowering(
      final ControlFlowBuilder flow,
      final KotlinStatements statements,
      final KotlinAccesses accesses,
      final KotlinAllowListGuards guards
  ) {
    this.flow = flow;
    this.statements = statements;
    this.accesses = accesses;
    this.guards = guards;
  }

  void ifStatement(final SyntaxNode node, final KotlinResult result) {
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
    statements.controlBody(thenBody, result);
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    if (hasElse) {
      flow.moveTo(elseBlock);
      statements.controlBody(elseBody, result);
      flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    }
    flow.moveTo(after);
    if (!hasElse && thenBody != null) {
      guards
          .ofIf(condition, thenBody)
          .ifPresent(name -> guarded(name, condition, node));
    }
  }

  /** Records the guard of {@code name}: past it the variable holds one of the allowed constants. */
  private void guarded(final String name, final SyntaxNode condition, final SyntaxNode node) {
    JavaAllowListGuards.record(
        flow,
        accesses.clean(name, condition.span(), node.span()),
        name,
        node.span()
    );
  }
}
