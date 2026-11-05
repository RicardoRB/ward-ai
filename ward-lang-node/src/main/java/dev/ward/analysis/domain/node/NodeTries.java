package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Lowers a TypeScript or JavaScript {@code try} statement with its catch and finally clauses. */
final class NodeTries {

  private static final String BLOCK = "block";

  private final ControlFlowBuilder flow;
  private final NodeStatements statements;

  NodeTries(final ControlFlowBuilder flow, final NodeStatements statements) {
    this.flow = flow;
    this.statements = statements;
  }

  boolean tryStatement(final List<SyntaxNode> parts) {
    final SyntaxNode catchClause = NodeFlowSyntax.firstOf(parts, "catchProduction");
    final SyntaxNode finallyClause = NodeFlowSyntax.firstOf(parts, "finallyProduction");
    flow.tries().pushTry(catchClause != null, finallyClause != null);
    statements.blockStatements(NodeFlowSyntax.firstOf(parts, BLOCK));
    flow.tries().endTryBody();
    if (catchClause != null) {
      flow.tries().beginCatch();
      statements.blockStatements(NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(catchClause), BLOCK));
      flow.tries().endCatch();
    }
    flow.tries().beginFinally();
    if (finallyClause != null) {
      statements.blockStatements(
          NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(finallyClause), BLOCK));
    }
    flow.tries().popTry();
    return true;
  }
}
