package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Follows the expressions made with one or two operands: a call, an await, a join, a sum. */
final class NodeProgramOperators {
  private static final Set<String> JOINING = Set.of("??", "||", "&&");
  private static final Set<String> TRANSPARENT = Set.of("as", "satisfies");
  private static final String AWAIT = "await";
  private static final String NOT_NULL = "!";
  private static final int RIGHT = 2;

  private final NodeProgramValues values;
  private final NodeProgramElements access;

  NodeProgramOperators(final NodeProgramValues values, final NodeProgramElements access) {
    this.values = values;
    this.access = access;
  }

  Optional<List<NodeProgramValues.Term>> twoParts(
      final NodeProgramFile file,
      final SyntaxNode expression,
      final List<SyntaxNode> parts,
      final int depth
  ) {
    final Optional<NodeProgramShapes.Call> call = NodeProgramShapes.call(expression);
    if (call.isPresent()) {
      return access.call(file, expression, call.get(), depth);
    }
    if (AWAIT.equals(NodeFlowSyntax.text(parts.get(0)))) {
      return values.of(file, parts.get(1), depth + 1);
    }
    if (NOT_NULL.equals(NodeFlowSyntax.text(parts.get(1)))) {
      return values.of(file, parts.get(0), depth + 1);
    }
    return NodeNumericShapes.unaryOrCall(parts)
        ? Optional.of(List.of(new NodeProgramValues.Term(file, expression)))
        : Optional.empty();
  }

  Optional<List<NodeProgramValues.Term>> threeParts(
      final NodeProgramFile file,
      final SyntaxNode expression,
      final List<SyntaxNode> parts,
      final int depth
  ) {
    final Optional<NodeProgramShapes.Member> member = NodeProgramShapes.member(expression);
    if (member.isPresent()) {
      return access.member(file, expression, member.get(), depth);
    }
    final String operator = NodeFlowSyntax.text(parts.get(1));
    if (JOINING.contains(operator)) {
      return access.union(file, depth, parts.get(0), parts.get(RIGHT));
    }
    if (TRANSPARENT.contains(operator)) {
      return values.of(file, parts.get(0), depth + 1);
    }
    return "+".equals(operator) || NodeNumericShapes.operator(operator)
        ? Optional.of(List.of(new NodeProgramValues.Term(file, expression)))
        : Optional.empty();
  }
}
