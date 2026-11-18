package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Tells whether two places of a Python module lie in branches of one {@code if} or {@code try}
 * statement that never both run: two blocks of an {@code if}/{@code elif}/{@code else}, or the body
 * of a {@code try} and one of its handlers, or two handlers.
 */
final class ExclusiveBranches {
  private static final String IF = "if_stmt";
  private static final String TRY = "try_stmt";
  private static final String BLOCK = "block";
  private static final String HANDLER = "except_clause";

  private ExclusiveBranches() {}

  /** Returns whether {@code first} and {@code second} are in exclusive blocks of one statement. */
  static boolean of(final SyntaxNode root, final Span first, final Span second) {
    if (IF.equals(root.kind()) || TRY.equals(root.kind())) {
      final List<int[]> blocks = alternatives(root);
      final int one = indexOf(blocks, first);
      final int other = indexOf(blocks, second);
      if (one >= 0 && other >= 0 && one != other) {
        return true;
      }
    }
    return root.children().stream()
        .filter(child -> covers(child, first) && covers(child, second))
        .anyMatch(child -> of(child, first, second));
  }

  /** The blocks that exclude each other: all of an {@code if}, the body and handlers of a try. */
  private static List<int[]> alternatives(final SyntaxNode statement) {
    final List<int[]> blocks = new ArrayList<>();
    final List<SyntaxNode> parts = statement.children();
    boolean handler = false;
    for (int index = 0; index < parts.size(); index++) {
      handler |= HANDLER.equals(parts.get(index).kind());
      if (BLOCK.equals(parts.get(index).kind())) {
        if (IF.equals(statement.kind()) || blocks.isEmpty() || handler) {
          blocks.add(new int[] {parts.get(index).span().startOffset(), end(statement, index)});
        }
        handler = false;
      }
    }
    return blocks;
  }

  /** A block node spans only its first token, so it ends where the next part of the statement starts. */
  private static int end(final SyntaxNode statement, final int index) {
    final List<SyntaxNode> parts = statement.children();
    return index + 1 < parts.size()
        ? parts.get(index + 1).span().startOffset()
        : statement.span().endOffset();
  }

  private static int indexOf(final List<int[]> blocks, final Span place) {
    for (int index = 0; index < blocks.size(); index++) {
      if (blocks.get(index) [0] <= place.startOffset() && place.endOffset() <= blocks.get(
          index
      ) [1]) {
        return index;
      }
    }
    return -1;
  }

  private static boolean covers(final SyntaxNode node, final Span place) {
    return node.span().startOffset() <= place.startOffset()
        && place.endOffset() <= node.span().endOffset();
  }
}
