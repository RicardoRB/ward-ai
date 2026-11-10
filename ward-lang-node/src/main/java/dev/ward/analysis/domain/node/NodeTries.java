package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Lowers a TypeScript or JavaScript {@code try} statement with its catch and finally clauses. */
final class NodeTries {

  private static final String BLOCK = "block";
  private static final String IDENTIFIER = "identifier";
  private static final String ASSIGNABLE = "assignable";

  private final ControlFlowBuilder flow;
  private final NodeStatements statements;
  private final NodeAccesses accesses;

  NodeTries(
      final ControlFlowBuilder flow, final NodeStatements statements, final NodeAccesses accesses) {
    this.flow = flow;
    this.statements = statements;
    this.accesses = accesses;
  }

  boolean tryStatement(final List<SyntaxNode> parts) {
    final SyntaxNode catchClause = NodeFlowSyntax.firstOf(parts, "catchProduction");
    final SyntaxNode finallyClause = NodeFlowSyntax.firstOf(parts, "finallyProduction");
    flow.tries().pushTry(catchClause != null, finallyClause != null);
    statements.blockStatements(NodeFlowSyntax.firstOf(parts, BLOCK));
    flow.tries().endTryBody();
    if (catchClause != null) {
      flow.tries().beginCatch();
      accesses.enterScope();
      flow.record(accesses.catchParameter(catchParameter(catchClause)), List.of());
      statements.blockStatements(NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(catchClause), BLOCK));
      accesses.exitScope();
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

  /**
   * Returns the parameter of a catch clause: an identifier in TypeScript, an assignable in
   * JavaScript, or null for a catch without a binding.
   */
  private static SyntaxNode catchParameter(final SyntaxNode catchClause) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(catchClause);
    final SyntaxNode identifier = NodeFlowSyntax.firstOf(parts, IDENTIFIER);
    return identifier != null ? identifier : NodeFlowSyntax.firstOf(parts, ASSIGNABLE);
  }
}
