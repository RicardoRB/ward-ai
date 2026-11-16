package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads documentation comments written as a run of {@code ///} lines or as one {@code /** ... *}
 * block. The tree has one comment node per {@code ///} line, so a check that reads only the node
 * before a declaration sees the last line of its documentation and nothing else (#567).
 */
final class DocComments {
  private static final String LINE = "///";
  private static final String BLOCK = "/**";
  private static final String TEXT = "text";

  private DocComments() {}

  /** Returns whether the node is a documentation comment. */
  static boolean isDoc(final SyntaxNode node) {
    final String text = node.fields().getOrDefault(TEXT, "");
    return SyntaxWalk.COMMENT_KIND.equals(node.kind())
        && (text.startsWith(LINE) || text.startsWith(BLOCK));
  }

  /**
   * Returns the documentation that ends right before a declaration: the documentation comments at
   * the start of {@code before}, which lists the siblings before the declaration, nearest first.
   * The comments are returned in source order; the list is empty when the declaration has none.
   */
  static List<SyntaxNode> leading(final List<SyntaxNode> before) {
    final List<SyntaxNode> comments = new ArrayList<>();
    for (final SyntaxNode sibling : before) {
      if (!isDoc(sibling)) {
        break;
      }
      comments.add(sibling);
    }
    Collections.reverse(comments);
    return comments;
  }

  /** Returns the lines of the comments, without the comment marks and the space after them. */
  static List<String> lines(final List<SyntaxNode> comments) {
    final List<String> lines = new ArrayList<>();
    for (final SyntaxNode comment : comments) {
      for (final String line : comment.fields().getOrDefault(TEXT, "").split("\n", -1)) {
        lines.add(
            line.strip().replaceFirst("^(///|/\\*\\*|\\*/|\\*)", "").replace("*/", "").strip()
        );
      }
    }
    return lines;
  }
}
