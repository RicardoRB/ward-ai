package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/**
 * Reads the value that an expression hands to a sink: the right side of an assignment {@code
 * target = value}, or one argument of a call.
 */
public final class NodeSinkValues {
  private static final String ARGUMENTS = "arguments";
  private static final int BINARY_PARTS = 3;
  private static final int CALL_PARTS = 2;
  private static final int VALUE_INDEX = 2;

  private NodeSinkValues() {}

  /**
   * The value of an assignment, or the first or last argument of a call.
   *
   * @param expression the {@code singleExpression} of the assignment or the call
   * @param first whether a call hands over its first argument, or its last
   * @return the value, or none when the expression is neither shape or the call has no argument
   */
  public static Optional<SyntaxNode> of(final SyntaxNode expression, final boolean first) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(expression);
    if (parts.size() == BINARY_PARTS && "=".equals(NodeFlowSyntax.text(parts.get(1)))) {
      return Optional.of(parts.get(VALUE_INDEX));
    }
    if (parts.size() != CALL_PARTS) {
      return Optional.empty();
    }
    final List<SyntaxNode> values = arguments(parts.get(1));
    if (values.isEmpty()) {
      return Optional.empty();
    }
    final SyntaxNode argument = values.get(first ? 0 : values.size() - 1);
    return Optional.of(
        Optional.ofNullable(
            NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(argument), "singleExpression")
        )
            .orElse(argument)
    );
  }

  private static List<SyntaxNode> arguments(final SyntaxNode node) {
    if (ARGUMENTS.equals(node.kind())) {
      return NodeCallShapes.argumentValues(node);
    }
    return NodeCallShapes.isParenthesized(node) ? NodeCallShapes.parenthesizedValues(
        node
    ) : List.of();
  }
}
