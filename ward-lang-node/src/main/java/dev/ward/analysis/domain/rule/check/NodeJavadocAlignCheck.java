package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports each javadoc comment whose leading asterisks are not one column right of the opening
 * slash, like Checkstyle's JavadocLeadingAsteriskAlign. The columns come from the comment's span
 * and its raw source lines; the finding is placed at the comment.
 */
public final class NodeJavadocAlignCheck implements RuleCheck {

  private static final String COMMENT = "comment";
  private static final String JAVADOC = "/**";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NodeJavadocAlignCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found);
    return List.copyOf(found);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> found) {
    if (COMMENT.equals(node.kind())) {
      found.addAll(misaligned(node));
    }
    node.children().forEach(child -> collect(child, found));
  }

  /** One finding per line whose leading asterisk is misaligned, placed at that asterisk. */
  private List<RuleMatch> misaligned(final SyntaxNode comment) {
    final String text = comment.fields().getOrDefault("text", "");
    if (!text.startsWith(JAVADOC) || !text.contains("\n")) {
      return List.of();
    }
    final Span span = comment.span();
    final List<RuleMatch> found = new ArrayList<>();
    final String[] lines = text.split("\n", -1);
    int lineStart = 0;
    for (int index = 0; index < lines.length; index++) {
      final String line = lines[index];
      final int star = line.indexOf('*');
      if (index > 0
          && star >= 0
          && line.substring(0, star).isBlank()
          && star != span.startColumn()) {
        found.add(at(span, index, lineStart + star, star));
      }
      lineStart += line.length() + 1;
    }
    return found;
  }

  /** The one-character span of the asterisk, on its own line of the source. */
  private RuleMatch at(
      final Span comment, final int lineIndex, final int offsetInText, final int column) {
    final int line = comment.startLine() + lineIndex;
    final int start = comment.startOffset() + offsetInText;
    return new RuleMatch(new Span(line, column + 1, line, column + 2, start, start + 1), message);
  }
}
