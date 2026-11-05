package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Recognises a call statement that the TypeScript grammar parses as a variable statement. For
 * {@code sink(b);} after a declaration, or at the start of a body, the grammar gives a variable
 * statement with no {@code var}, {@code let} or {@code const} modifier, whose only declaration is
 * the callee name and a parenthesized argument sequence. Such a statement is an expression.
 */
final class NodeStatementCalls {

  private static final String VARIABLE_LIST = "variableDeclarationList";
  private static final String VARIABLE_DECLARATION = "variableDeclaration";
  private static final String SEQUENCE = "expressionSequence";
  private static final String OPEN = "(";
  private static final String CLOSE = ")";
  private static final int DECLARATION_PARTS = 2;
  private static final int SINGLE_DECLARATION = 1;

  /** A call read from a statement: its callee and its argument nodes, in order. */
  record Call(SyntaxNode callee, List<SyntaxNode> arguments) {}

  private NodeStatementCalls() {}

  /**
   * Returns the call of a statement the grammar parsed as a variable declaration, if it is one.
   *
   * @param statement a variable statement node
   * @return the call, or empty when the statement is a real declaration or not a call
   */
  static Optional<Call> callOf(final SyntaxNode statement) {
    if (!isExpressionStatement(statement)) {
      return Optional.empty();
    }
    final SyntaxNode list = NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(statement), VARIABLE_LIST);
    final List<SyntaxNode> declarations =
        NodeFlowSyntax.allOf(NodeFlowSyntax.parts(list), VARIABLE_DECLARATION);
    if (declarations.size() != SINGLE_DECLARATION) {
      return Optional.empty();
    }
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(declarations.get(0));
    if (parts.size() != DECLARATION_PARTS || !parenthesized(parts.get(1))) {
      return Optional.empty();
    }
    return Optional.of(new Call(parts.get(0), sequenceValues(parts.get(1))));
  }

  /** Reports whether a variable statement has no declaring modifier, so it is an expression. */
  static boolean isExpressionStatement(final SyntaxNode statement) {
    if (!"variableStatement".equals(statement.kind()) || NodeSyntax.modifies(statement)) {
      return false;
    }
    final SyntaxNode list = NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(statement), VARIABLE_LIST);
    return list != null && !NodeSyntax.modifies(list);
  }

  private static boolean parenthesized(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    return parts.size() >= DECLARATION_PARTS
        && OPEN.equals(NodeFlowSyntax.text(parts.get(0)))
        && CLOSE.equals(NodeFlowSyntax.text(parts.get(parts.size() - 1)));
  }

  private static List<SyntaxNode> sequenceValues(final SyntaxNode parenthesized) {
    final List<SyntaxNode> values = new ArrayList<>();
    final SyntaxNode sequence =
        NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(parenthesized), SEQUENCE);
    if (sequence != null) {
      for (final SyntaxNode child : sequence.children()) {
        if (!child.children().isEmpty()) {
          values.add(child);
        }
      }
    }
    return values;
  }
}
