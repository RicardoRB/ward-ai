package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;

/** Finds the argument expressions of a Java method call. */
final class CallArgumentNodes {

  private static final String EXPRESSION_LIST = "expressionList";
  private static final String METHOD_CALL = "methodCall";
  private static final String EXPRESSION = "expression";

  private CallArgumentNodes() {}

  static Optional<SyntaxNode> first(final SyntaxNode call) {
    return call.children().stream()
        .filter(part -> "methodCall".equals(part.kind()))
        .findFirst()
        .flatMap(
            invoked ->
                SyntaxWalk.descendants(invoked).stream()
                    .filter(node -> "expressionList".equals(node.kind()))
                    .findFirst())
        .flatMap(
            list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).findFirst());
  }

  /** The argument expressions of the call, in order; none when the call has no method part. */
  static List<SyntaxNode> all(final SyntaxNode call) {
    return call.children().stream()
        .filter(part -> METHOD_CALL.equals(part.kind()))
        .flatMap(method -> SyntaxWalk.descendants(method).stream())
        .filter(node -> EXPRESSION_LIST.equals(node.kind()))
        .findFirst()
        .map(SyntaxNode::children)
        .orElse(List.of())
        .stream()
        .filter(node -> EXPRESSION.equals(node.kind()))
        .toList();
  }
}
