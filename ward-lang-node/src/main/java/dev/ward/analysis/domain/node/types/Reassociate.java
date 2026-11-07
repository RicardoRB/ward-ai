package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repairs the expressions that the grammar reads as a name followed by an expression: {@code f(x)},
 * {@code a + 1}, {@code names[0]} and chains such as {@code load(1).then(g)}. The rule {@code
 * identifierName singleExpression?} makes the parser take the rest of the expression as one operand
 * of the name, so the tree has the wrong shape. The repaired tree has the operators in their
 * precedence order and keeps the span of every node that the grammar produced. An expression that
 * cannot be repaired is left as it is.
 */
final class Reassociate {
  private static final String SINGLE = "singleExpression";
  private static final int INDEX_PARTS = 3;

  private Reassociate() {}

  /** Returns {@code node} with its misshapen expressions repaired. */
  static SyntaxNode of(final SyntaxNode node) {
    if (node.children().isEmpty()) {
      return node;
    }
    final SyntaxNode current =
        new SyntaxNode(
            node.kind(),
            node.span(),
            node.fields(),
            node.children().stream().map(Reassociate::of).toList());
    if (!SINGLE.equals(node.kind())) {
      return current;
    }
    final SyntaxNode repaired = repair(current).orElse(current);
    return NewExpressions.repair(repaired).orElseGet(() -> rotate(repaired));
  }

  /**
   * The grammar hands the tail of {@code a * b + c} to {@code b}, which leaves a right operand that
   * binds looser than its operator; this turns it into {@code (a * b) + c}.
   */
  private static SyntaxNode rotate(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (parts.size() != INDEX_PARTS
        || !ChainItem.PRECEDENCE.containsKey(Exprs.token(parts.get(1)))) {
      return node;
    }
    final List<SyntaxNode> right = TreeKit.parts(parts.get(2));
    final boolean looser =
        right.size() == INDEX_PARTS
            && !Exprs.isParenthesized(right)
            && ChainItem.PRECEDENCE.containsKey(Exprs.token(right.get(1)))
            && ChainItem.PRECEDENCE.get(Exprs.token(right.get(1)))
                <= ChainItem.PRECEDENCE.get(Exprs.token(parts.get(1)));
    if (!looser) {
      return node;
    }
    final SyntaxNode left =
        new SyntaxNode(
            SINGLE,
            ChainBuilder.cover(parts.get(0).span(), right.get(0).span()),
            Map.of(),
            List.of(parts.get(0), parts.get(1), right.get(0)));
    return rotate(
        new SyntaxNode(
            SINGLE, node.span(), Map.of(), List.of(rotate(left), right.get(1), right.get(2))));
  }

  private static Optional<SyntaxNode> repair(final SyntaxNode node) {
    final List<SyntaxNode> parts = TreeKit.parts(node);
    if (parts.size() != 2
        || !Exprs.IDENTIFIER_NAME.equals(parts.get(0).kind())
        || !SINGLE.equals(parts.get(1).kind())) {
      return Optional.empty();
    }
    final Optional<List<ChainItem>> items = ChainFlattener.flatten(parts.get(1));
    if (items.isEmpty()) {
      return Optional.empty();
    }
    final SyntaxNode first = wrap(parts.get(0));
    final SyntaxNode built = ChainBuilder.build(first, items.get());
    return Optional.of(new SyntaxNode(SINGLE, node.span(), Map.of(), built.children()));
  }

  private static SyntaxNode wrap(final SyntaxNode name) {
    return new SyntaxNode(SINGLE, name.span(), Map.of(), List.of(name));
  }
}
