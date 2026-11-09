package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Decides whether an expression statement discards its value: the statement is not a declaration, a
 * return or the single expression of a closure, function or getter body, which return it.
 */
final class SwiftDiscardedStatement {

  private static final String STATEMENT = "statement";
  private static final String STATEMENTS = "statements";
  private static final String STATEMENT_LIST = "statements_impl";
  private static final Set<String> BODIES =
      Set.of("closure_expression", "function_body", "getter_clause");
  private static final Set<String> VALUE_KEYWORDS = Set.of("return", "let", "var");
  private static final int SOLE_STATEMENT = 1;

  private SwiftDiscardedStatement() {}

  /** Whether the expression whose ancestors are given is a statement of its own. */
  static boolean isDiscarded(final List<SyntaxNode> ancestors) {
    for (int i = ancestors.size() - 1; i >= 0; i--) {
      final String kind = ancestors.get(i).kind();
      if (STATEMENT.equals(kind)) {
        return !isSoleBodyExpression(ancestors, i) && !hasValueKeyword(ancestors.get(i));
      }
      if (!"expression".equals(kind)
          && !kind.endsWith("_expression")
          && !"binary_expressions".equals(kind)) {
        return false;
      }
    }
    return false;
  }

  /** A single statement of a closure, function or getter body is an implicit return. */
  private static boolean isSoleBodyExpression(final List<SyntaxNode> ancestors, final int index) {
    return countStatements(ancestors, index) == SOLE_STATEMENT
        && ancestors.subList(0, index).stream().anyMatch(a -> BODIES.contains(a.kind()));
  }

  /** The statements of the nearest enclosing statement list, counted without nested blocks. */
  private static int countStatements(final List<SyntaxNode> ancestors, final int index) {
    final int owner = enclosingStatementList(ancestors, index);
    return owner < 0 ? 0 : directStatements(ancestors.get(owner), new ArrayList<>()).size();
  }

  private static int enclosingStatementList(final List<SyntaxNode> ancestors, final int index) {
    for (int i = index - 1; i >= 0; i--) {
      if (STATEMENTS.equals(ancestors.get(i).kind())) {
        return i;
      }
    }
    return -1;
  }

  private static List<SyntaxNode> directStatements(
      final SyntaxNode node, final List<SyntaxNode> out) {
    for (final SyntaxNode child : node.children()) {
      if (STATEMENT.equals(child.kind())) {
        out.add(child);
      } else if (STATEMENTS.equals(child.kind()) || STATEMENT_LIST.equals(child.kind())) {
        directStatements(child, out);
      }
    }
    return out;
  }

  private static boolean hasValueKeyword(final SyntaxNode statement) {
    return statement.children().stream()
        .flatMap(child -> SwiftTrees.leaves(child).stream())
        .anyMatch(leaf -> VALUE_KEYWORDS.contains(leaf.fields().get("text")));
  }
}
