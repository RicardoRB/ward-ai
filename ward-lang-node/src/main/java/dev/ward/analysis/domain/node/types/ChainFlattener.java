package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Helpers split out of {@link Reassociate}. */
final class ChainFlattener {
  private static final String SINGLE = "singleExpression";
  private static final String ARGUMENTS = "arguments";
  private static final String SEQUENCE = "expressionSequence";
  private static final int INDEX_PARTS = 3;
  private static final Set<String> MEMBER_TOKENS = Set.of(".", "?.", "!", "?");

  private ChainFlattener() {}

  static Optional<List<ChainItem>> flatten(final SyntaxNode rest) {
    final List<SyntaxNode> parts = TreeKit.parts(rest);
    if (parts.isEmpty()) {
      return Optional.empty();
    }
    if (Exprs.isParenthesized(parts)) {
      return Optional.of(
          new ArrayList<>(
              List.of(new ChainItem(ChainItem.Kind.CALL, "", List.of(arguments(rest))))));
    }
    if (parts.size() == 1 && "arrayLiteral".equals(parts.get(0).kind())) {
      return Optional.of(
          new ArrayList<>(List.of(new ChainItem(ChainItem.Kind.INDEX, "", index(parts.get(0))))));
    }
    final String first = Exprs.token(parts.get(0));
    if (parts.size() == 2
        && ("+".equals(first) || "-".equals(first))
        && SINGLE.equals(parts.get(1).kind())) {
      return Optional.of(
          new ArrayList<>(
              List.of(new ChainItem(ChainItem.Kind.BINARY, first, List.of(parts.get(1))))));
    }
    if (SINGLE.equals(parts.get(0).kind())) {
      return spine(parts);
    }
    return Optional.empty();
  }

  static Optional<List<ChainItem>> spine(final List<SyntaxNode> parts) {
    final Optional<List<ChainItem>> inner = flatten(parts.get(0));
    final Optional<ChainItem> next = suffix(parts.subList(1, parts.size()));
    if (inner.isEmpty() || next.isEmpty()) {
      return Optional.empty();
    }
    inner.get().add(next.get());
    return inner;
  }

  static Optional<ChainItem> suffix(final List<SyntaxNode> rest) {
    final Optional<ChainItem> postfix = postfixItem(rest);
    return postfix.isPresent() ? postfix : binaryItem(rest);
  }

  static Optional<ChainItem> postfixItem(final List<SyntaxNode> rest) {
    final String first = Exprs.token(rest.get(0));
    if (rest.size() == 1 && ARGUMENTS.equals(rest.get(0).kind())) {
      return Optional.of(new ChainItem(ChainItem.Kind.CALL, "", List.of(rest.get(0))));
    }
    if (rest.size() == 1 && "!".equals(first)) {
      return Optional.of(new ChainItem(ChainItem.Kind.NONNULL, first, rest));
    }
    if (MEMBER_TOKENS.contains(first)) {
      return Optional.of(new ChainItem(ChainItem.Kind.MEMBER, first, rest));
    }
    return "[".equals(first) && rest.size() == INDEX_PARTS
        ? Optional.of(new ChainItem(ChainItem.Kind.INDEX, "", rest))
        : Optional.empty();
  }

  static Optional<ChainItem> binaryItem(final List<SyntaxNode> rest) {
    final String first = Exprs.token(rest.get(0));
    return rest.size() == 2 && ChainItem.PRECEDENCE.containsKey(first)
        ? Optional.of(new ChainItem(ChainItem.Kind.BINARY, first, List.of(rest.get(1))))
        : Optional.empty();
  }

  static SyntaxNode arguments(final SyntaxNode group) {
    return new SyntaxNode(ARGUMENTS, group.span(), Map.of(), group.children());
  }

  static List<SyntaxNode> index(final SyntaxNode array) {
    final List<SyntaxNode> inner = new ArrayList<>();
    final SyntaxNode list = TreeKit.child(array, "elementList");
    if (list != null) {
      for (final SyntaxNode element : TreeKit.children(list, "arrayElement")) {
        inner.addAll(TreeKit.children(element, SINGLE));
      }
    }
    final List<SyntaxNode> parts = TreeKit.parts(array);
    final SyntaxNode open = parts.get(0);
    final SyntaxNode close = parts.get(parts.size() - 1);
    final Span span =
        inner.isEmpty()
            ? open.span()
            : ChainBuilder.cover(inner.get(0).span(), inner.get(inner.size() - 1).span());
    return List.of(open, new SyntaxNode(SEQUENCE, span, Map.of(), inner), close);
  }
}
