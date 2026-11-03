package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;

/** Counts the decision points of a function, the terms of its cyclomatic complexity. */
final class Decisions {

  /** Statements and clauses that each add one decision point. */
  private static final Set<String> BRANCH =
      Set.of("ifStatement", "iterationStatement", "caseClause", "catchProduction");

  /** The expression kind that holds logical and conditional operator tokens as direct children. */
  private static final String EXPRESSION = "singleExpression";

  /** Operator tokens that each add one decision point when they appear in an expression. */
  private static final Set<String> DECISION_TOKEN = Set.of("'&&'", "'||'", "'??'", "'?'");

  private Decisions() {}

  /** Counts the decision points of a function, not counting nested functions. */
  static int count(final SyntaxNode function) {
    int count = 0;
    for (final SyntaxNode child : function.children()) {
      count += decisionsIn(child);
    }
    return count;
  }

  private static int decisionsIn(final SyntaxNode node) {
    if (SyntaxTrees.isFunction(node)) {
      return 0;
    }
    int count = decisionAt(node);
    for (final SyntaxNode child : node.children()) {
      count += decisionsIn(child);
    }
    return count;
  }

  private static int decisionAt(final SyntaxNode node) {
    if (BRANCH.contains(node.kind())) {
      return 1;
    }
    if (!EXPRESSION.equals(node.kind())) {
      return 0;
    }
    int count = 0;
    for (final SyntaxNode child : node.children()) {
      if (DECISION_TOKEN.contains(child.kind())) {
        count++;
      }
    }
    return count;
  }
}
