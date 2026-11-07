package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Checks that every row after the first of a multi-line block comment that is not Javadoc starts
 * with an asterisk, blank rows included, as Checkstyle's MultilineCommentLeadingAsteriskPresence
 * does.
 */
public final class BlockCommentAsteriskCheck implements UnitCheck {

  private static final String OPEN = "/*";
  private static final String JAVADOC_OPEN = "/**";
  private static final char ASTERISK = '*';

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public BlockCommentAsteriskCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (isBlockComment(node)) {
        for (final JavadocRows.Row row : JavadocRows.of(node)) {
          if (row.index() > 0 && !startsWithAsterisk(row)) {
            matches.add(
                CheckSupport.at(row.at(Math.min(row.lead(), row.text().length())), message));
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isBlockComment(final SyntaxNode node) {
    final String text = node.fields().get("text");
    return SyntaxWalk.COMMENT_KIND.equals(node.kind())
        && text != null
        && text.startsWith(OPEN)
        && (!text.startsWith(JAVADOC_OPEN) || "/**/".equals(text));
  }

  private static boolean startsWithAsterisk(final JavadocRows.Row row) {
    final int lead = row.lead();
    return lead < row.text().length() && row.text().charAt(lead) == ASTERISK;
  }
}
