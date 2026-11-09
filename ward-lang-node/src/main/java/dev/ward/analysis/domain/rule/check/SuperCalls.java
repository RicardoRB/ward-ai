package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/** Finds the {@code super(...)} calls of a syntax tree, and reads the first token under a node. */
final class SuperCalls {

  private static final String SUPER = "'super'";
  private static final String CALL = "singleExpression";
  private static final String ARGUMENTS = "arguments";

  private SuperCalls() {}

  /** The {@code super(...)} calls within {@code nodes}, in source order. */
  static List<SyntaxNode> in(final List<SyntaxNode> nodes) {
    final List<SyntaxNode> calls = new ArrayList<>();
    for (final SyntaxNode node : nodes) {
      collect(node, calls);
    }
    return calls;
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> calls) {
    if (isCall(node)) {
      calls.add(node);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, calls);
    }
  }

  /** A call whose callee's first token is the super keyword, and which has arguments. */
  private static boolean isCall(final SyntaxNode node) {
    return CALL.equals(node.kind())
        && !node.children().isEmpty()
        && SUPER.equals(firstLeafKind(node.children().get(0)))
        && node.children().stream().anyMatch(c -> ARGUMENTS.equals(c.kind()));
  }

  /** The kind of the first token under {@code node}. */
  static String firstLeafKind(final SyntaxNode node) {
    SyntaxNode current = node;
    while (!current.children().isEmpty()) {
      current = current.children().get(0);
    }
    return current.kind();
  }
}
