package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.rule.check.RuleCheck;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reports the first doc comment of a run of doc comments that is followed by a plain comment before
 * any declaration, like SwiftLint's {@code orphaned_doc_comment}. A doc comment is {@code ///} or
 * {@code /**}; {@code ////} and {@code /***} are plain comments, as in SwiftLint.
 */
public final class SwiftOrphanedDocCommentCheck implements RuleCheck {

  private static final String COMMENT = "comment";
  private static final String DOC_LINE = "///";
  private static final String DOC_BLOCK = "/**";
  private static final String PLAIN_LINE = "////";
  private static final String PLAIN_BLOCK = "/***";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SwiftOrphanedDocCommentCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxNode> items = new ArrayList<>(SwiftTrees.leaves(root));
    items.addAll(SwiftTrees.descendants(root, COMMENT));
    items.sort(Comparator.comparingInt(item -> item.span().startOffset()));
    final List<RuleMatch> matches = new ArrayList<>();
    int index = 0;
    while (index < items.size()) {
      final int next = endOfDocRun(items, index);
      if (next == index) {
        index++;
      } else if (next < items.size() && isPlainComment(items.get(next))) {
        matches.add(SwiftTrees.at(items.get(index), message));
        index = next + 1;
      } else {
        index = next;
      }
    }
    return List.copyOf(matches);
  }

  /** The index after the run of doc comments that starts at {@code index}, or {@code index}. */
  private static int endOfDocRun(final List<SyntaxNode> items, final int index) {
    int next = index;
    while (next < items.size() && isDoc(items.get(next))) {
      next++;
    }
    return next;
  }

  private static boolean isDoc(final SyntaxNode item) {
    if (!COMMENT.equals(item.kind())) {
      return false;
    }
    final String text = item.fields().get("text");
    return text.startsWith(DOC_LINE) && !text.startsWith(PLAIN_LINE)
        || text.startsWith(DOC_BLOCK) && !text.startsWith(PLAIN_BLOCK);
  }

  private static boolean isPlainComment(final SyntaxNode item) {
    return COMMENT.equals(item.kind()) && !isDoc(item);
  }
}
