package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Lowers a TypeScript or JavaScript {@code switch}, whose clauses fall through unless they break.
 */
final class NodeSwitches {

  private static final String CASE_CLAUSE = "caseClause";
  private static final String DEFAULT_CLAUSE = "defaultClause";
  private static final String CASE_CLAUSES = "caseClauses";
  private static final int SELECTOR_INDEX = 2;

  private final ControlFlowBuilder flow;
  private final NodeStatements statements;

  NodeSwitches(final ControlFlowBuilder flow, final NodeStatements statements) {
    this.flow = flow;
    this.statements = statements;
  }

  boolean switchOn(final List<SyntaxNode> parts) {
    final SyntaxNode selector = parts.get(SELECTOR_INDEX);
    NodeSteps.add(flow, selector, NodeExpressions.mayThrow(selector));
    final int head = flow.current();
    final int after = flow.newBlock();
    flow.jumps().pushSwitch(after);
    boolean hasDefault = false;
    boolean fallsIn = false;
    for (final SyntaxNode clause : clauses(NodeFlowSyntax.firstOf(parts, "caseBlock"))) {
      final int entry = flow.newBlock();
      flow.edge(head, entry, FlowEdgeKind.NORMAL);
      if (fallsIn) {
        flow.edge(flow.current(), entry, FlowEdgeKind.NORMAL);
      }
      flow.moveTo(entry);
      hasDefault |= DEFAULT_CLAUSE.equals(clause.kind());
      clauseStatements(clause);
      fallsIn = true;
    }
    flow.jumps().popFrame();
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    if (!hasDefault) {
      flow.edge(head, after, FlowEdgeKind.NORMAL);
    }
    flow.moveTo(after);
    return true;
  }

  private void clauseStatements(final SyntaxNode clause) {
    for (final SyntaxNode statement : NodeFlowSyntax.statements(clause)) {
      statements.statement(statement, null);
    }
  }

  /** Lists the case and default clauses of a case block, in source order. */
  private static List<SyntaxNode> clauses(final SyntaxNode caseBlock) {
    final List<SyntaxNode> clauses = new ArrayList<>();
    for (final SyntaxNode part : NodeFlowSyntax.parts(caseBlock)) {
      if (CASE_CLAUSES.equals(part.kind())) {
        clauses.addAll(NodeFlowSyntax.allOf(NodeFlowSyntax.parts(part), CASE_CLAUSE));
      } else if (CASE_CLAUSE.equals(part.kind()) || DEFAULT_CLAUSE.equals(part.kind())) {
        clauses.add(part);
      }
    }
    return clauses;
  }
}
