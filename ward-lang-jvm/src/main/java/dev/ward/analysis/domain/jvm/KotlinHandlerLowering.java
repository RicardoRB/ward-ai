package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Lowers the {@code try} and {@code when} statements of a Kotlin body. */
final class KotlinHandlerLowering {

  private static final String BLOCK = "block";
  private static final String BODY = "controlStructureBody";
  private static final String EXPRESSION = "expression";
  private static final String ELSE = "else";

  private final ControlFlowBuilder flow;
  private final KotlinStatements statements;

  KotlinHandlerLowering(final ControlFlowBuilder flow, final KotlinStatements statements) {
    this.flow = flow;
    this.statements = statements;
  }

  void tryStatement(final SyntaxNode node, final KotlinResult result) {
    final List<SyntaxNode> catches = KotlinFlowSyntax.children(node, "catchBlock");
    final SyntaxNode finallyBlock = KotlinFlowSyntax.child(node, "finallyBlock");
    flow.tries().pushTry(!catches.isEmpty(), finallyBlock != null);
    statements.block(KotlinFlowSyntax.child(node, BLOCK), result);
    flow.tries().endTryBody();
    for (final SyntaxNode clause : catches) {
      flow.tries().beginCatch();
      flow.record(
          List.of(
              KotlinAccesses.catchParameter(KotlinFlowSyntax.child(clause, "simpleIdentifier"))),
          List.of());
      statements.block(KotlinFlowSyntax.child(clause, BLOCK), result);
      flow.tries().endCatch();
    }
    flow.tries().beginFinally();
    if (finallyBlock != null) {
      statements.block(KotlinFlowSyntax.child(finallyBlock, BLOCK));
    }
    flow.tries().popTry();
  }

  /**
   * A {@code when}: each entry tests its conditions in turn, so an entry's block follows the tests
   * of the entries before it; an {@code else} entry takes what no test caught.
   */
  void whenStatement(final SyntaxNode node, final KotlinResult result) {
    final SyntaxNode subject = KotlinFlowSyntax.child(node, EXPRESSION);
    if (subject != null) {
      statements.expression(subject, KotlinFlowSyntax.mayThrow(subject));
    }
    final int after = flow.newBlock();
    boolean closed = false;
    for (final SyntaxNode entry : KotlinFlowSyntax.children(node, "whenEntry")) {
      if (KotlinFlowSyntax.hasToken(entry, ELSE)) {
        elseEntry(entry, after, result);
        closed = true;
        break;
      }
      entry(entry, after, result);
    }
    if (!closed) {
      flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    }
    flow.moveTo(after);
  }

  private void entry(final SyntaxNode entry, final int after, final KotlinResult result) {
    for (final SyntaxNode test : KotlinFlowSyntax.parts(entry)) {
      if ("whenCondition".equals(test.kind()) || "whenGuard".equals(test.kind())) {
        statements.expression(test, KotlinFlowSyntax.mayThrow(test));
      }
    }
    final int then = flow.newBlock();
    final int next = flow.newBlock();
    flow.branch(then, next);
    flow.moveTo(then);
    run(KotlinFlowSyntax.child(entry, BODY), result);
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(next);
  }

  private void elseEntry(final SyntaxNode entry, final int after, final KotlinResult result) {
    final int then = flow.newBlock();
    flow.edge(flow.current(), then, FlowEdgeKind.NORMAL);
    flow.moveTo(then);
    run(KotlinFlowSyntax.child(entry, BODY), result);
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
  }

  private void run(final SyntaxNode body, final KotlinResult result) {
    if (body != null) {
      statements.controlBody(body, result);
    }
  }
}
