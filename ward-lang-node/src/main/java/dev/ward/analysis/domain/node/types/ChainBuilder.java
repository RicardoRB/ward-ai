package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** Helpers split out of {@link Reassociate}. */
final class ChainBuilder {
  private static final String SINGLE = "singleExpression";

  private ChainBuilder() {}

  static SyntaxNode build(final SyntaxNode first, final List<ChainItem> items) {
    final Deque<SyntaxNode> operands = new ArrayDeque<>();
    final Deque<ChainItem> operators = new ArrayDeque<>();
    operands.push(first);
    for (final ChainItem item : items) {
      if (item.kind() == ChainItem.Kind.BINARY) {
        while (!operators.isEmpty()
            && ChainItem.PRECEDENCE.get(operators.peek().op())
                >= ChainItem.PRECEDENCE.get(item.op())) {
          reduce(operands, operators);
        }
        operators.push(item);
        operands.push(item.nodes().get(0));
      } else {
        operands.push(postfix(operands.pop(), item));
      }
    }
    while (!operators.isEmpty()) {
      reduce(operands, operators);
    }
    return operands.pop();
  }

  static void reduce(final Deque<SyntaxNode> operands, final Deque<ChainItem> operators) {
    final SyntaxNode right = operands.pop();
    final SyntaxNode left = operands.pop();
    final ChainItem operator = operators.pop();
    final SyntaxNode token = operatorToken(operator, left.span());
    operands.push(
        new SyntaxNode(
            SINGLE, cover(left.span(), right.span()), Map.of(), List.of(left, token, right)));
  }

  static SyntaxNode operatorToken(final ChainItem operator, final Span span) {
    return new SyntaxNode(operator.op(), span, Map.of("text", operator.op()), List.of());
  }

  static SyntaxNode postfix(final SyntaxNode base, final ChainItem item) {
    final List<SyntaxNode> children = new ArrayList<>();
    children.add(base);
    children.addAll(item.nodes());
    final SyntaxNode last = item.nodes().get(item.nodes().size() - 1);
    return new SyntaxNode(SINGLE, cover(base.span(), last.span()), Map.of(), children);
  }

  static Span cover(final Span from, final Span to) {
    return new Span(
        from.startLine(),
        from.startColumn(),
        to.endLine(),
        to.endColumn(),
        from.startOffset(),
        to.endOffset());
  }
}
