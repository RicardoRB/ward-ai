package dev.ward.analysis.domain.flow.taint;

import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/**
 * Finds the returned value of a return statement in the shared syntax shape: the Java statement
 * whose first token is {@code return}, and the Node {@code returnStatement}. Both put the value
 * right after the keyword.
 */
final class TaintReturns {
  private static final String RETURN = "return";
  private static final String RETURN_STATEMENT = "returnStatement";
  private static final String TEXT = "text";
  private static final String SEMICOLON = ";";
  private static final int VALUE_INDEX = 1;

  private TaintReturns() {}

  /** Returns the span of the value that a return statement gives back, if the node is one. */
  static Optional<Span> value(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    if (children.size() <= VALUE_INDEX || !isReturn(node)) {
      return Optional.empty();
    }
    final SyntaxNode value = children.get(VALUE_INDEX);
    return SEMICOLON.equals(value.fields().get(TEXT))
        ? Optional.empty()
        : Optional.of(value.span());
  }

  /** Reports whether a node of the graph is a return statement that gives a value back. */
  static boolean returnsValue(final ControlFlowGraph graph) {
    return graph.blocks().stream()
        .flatMap(block -> block.nodes().stream())
        .anyMatch(node -> value(node).isPresent());
  }

  /** Reports whether a node is a return statement, with or without a value. */
  static boolean isReturnStatement(final SyntaxNode node) {
    return RETURN_STATEMENT.equals(node.kind())
        || !node.children().isEmpty() && RETURN.equals(node.children().get(0).fields().get(TEXT));
  }

  private static boolean isReturn(final SyntaxNode node) {
    return RETURN_STATEMENT.equals(node.kind())
        || RETURN.equals(node.children().get(0).fields().get(TEXT));
  }
}
