package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.function.Predicate;

/** Finds nodes of a Python syntax tree and reads the text of their tokens. */
final class DigestTree {
  static final int SINGLE = 1;
  static final String OPEN_CALL = "'('";
  static final String OPEN_INDEX = "'['";
  private static final String ATOM_EXPR = "atom_expr";
  private static final String STRING = "STRING";

  private DigestTree() {}

  static boolean isCallTrailer(final SyntaxNode node) {
    return "trailer".equals(node.kind()) && OPEN_CALL.equals(node.children().get(0).kind());
  }

  static String text(final SyntaxNode node) {
    return node.fields().getOrDefault("text", "");
  }

  /** The text of the tokens of the nodes, joined: {@code os.path.join} for its three parts. */
  static String tokens(final List<SyntaxNode> parts) {
    final StringBuilder text = new StringBuilder();
    parts.forEach(part -> leaves(part, text));
    return text.toString();
  }

  private static void leaves(final SyntaxNode node, final StringBuilder text) {
    if (node.children().isEmpty()) {
      text.append(text(node));
    }
    node.children().forEach(child -> leaves(child, text));
  }

  /** The leaf that a chain of single-child nodes ends in, or the first node that branches. */
  static SyntaxNode collapse(final SyntaxNode node) {
    SyntaxNode current = node;
    while (current.children().size() == SINGLE) {
      current = current.children().get(0);
    }
    return current;
  }

  /**
   * Fills {@code path} with the nodes from {@code node} down to the first node, in tree order, that
   * the test accepts. The whole tree is searched, as the span of a block can be shorter than the
   * statements in it.
   */
  private static boolean find(
      final SyntaxNode node,
      final Predicate<SyntaxNode> accepts,
      final List<SyntaxNode> path
  ) {
    path.add(node);
    if (accepts.test(node)) {
      return true;
    }
    for (final SyntaxNode child : node.children()) {
      if (find(child, accepts, path)) {
        return true;
      }
    }
    path.remove(path.size() - 1);
    return false;
  }

  /** The atom_expr that starts at the callee and calls it: the call trailer starts where it ends. */
  static boolean pathToCall(final SyntaxNode root, final Span site, final List<SyntaxNode> path) {
    return find(
        root,
        node -> ATOM_EXPR.equals(node.kind())
            && node.span().startOffset() == site.startOffset()
            && node.children().stream().anyMatch(part -> isCallAt(part, site)),
        path
    );
  }

  private static boolean isCallAt(final SyntaxNode node, final Span site) {
    return isCallTrailer(node) && node.span().startOffset() == site.endOffset();
  }

  /** The leaf of the read, or the string leaf that holds it when it is read in an f-string. */
  static boolean pathToRead(final SyntaxNode root, final Span read, final List<SyntaxNode> path) {
    return find(
        root,
        node -> node.children().isEmpty()
            && node.span().startOffset() == read.startOffset()
            && node.span().endOffset() == read.endOffset(),
        path
    )
        || find(
            root,
            node -> node.children().isEmpty()
                && STRING.equals(node.kind())
                && node.span().startOffset() <= read.startOffset()
                && node.span().endOffset() >= read.endOffset(),
            path
        );
  }
}
