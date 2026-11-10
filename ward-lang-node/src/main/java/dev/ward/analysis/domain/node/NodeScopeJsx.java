package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Set;

/** Reads the names that JSX tags refer to in scope analysis. */
final class NodeScopeJsx {

  private static final Set<String> MEMBER_OPERATORS = Set.of(".", "?.");

  private final NodeScopeExpressions expressions;

  NodeScopeJsx(final NodeScopeExpressions expressions) {
    this.expressions = expressions;
  }

  /**
   * Handles a JSX tag name. A component such as {@code <Button>} or a member such as {@code <a.B>}
   * reads the binding of its first name; a lower-case intrinsic tag such as {@code <div>} names no
   * binding, and neither does a namespaced {@code <svg:rect>}.
   */
  void jsxElementName(final SyntaxNode node) {
    final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
    final SyntaxNode first = parts.get(0);
    final boolean member =
        parts.size() > 1 && MEMBER_OPERATORS.contains(NodeFlowSyntax.text(parts.get(1)));
    if (member || isComponentName(NodeFlowSyntax.text(first))) {
      expressions.read(first);
    }
  }

  private static boolean isComponentName(final String name) {
    return !name.isEmpty() && Character.isUpperCase(name.charAt(0));
  }
}
