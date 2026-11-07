package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Checks the leading asterisks of the rows of a Javadoc comment, as Checkstyle's
 * JavadocLeadingAsteriskAlign, JavadocMissingLeadingAsterisk and
 * JavadocMissingWhitespaceAfterAsterisk do. The first row is never checked.
 */
public final class JavadocAsteriskCheck implements UnitCheck {

  /** What the check looks for in each row after the first. */
  public enum Mode {
    /** An asterisk that is not under the first asterisk of the opening delimiter. */
    ALIGN,
    /** A row with text and no leading asterisk. */
    MISSING,
    /** A leading asterisk that is not followed by a space. */
    WHITESPACE
  }

  private static final char ASTERISK = '*';
  private static final char SLASH = '/';

  private final Mode mode;
  private final String message;

  /**
   * Creates the check.
   *
   * @param mode what to look for
   * @param message the message of each finding
   */
  public JavadocAsteriskCheck(final Mode mode, final String message) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (JavadocTags.isJavadoc(node)) {
        for (final JavadocRows.Row row : JavadocRows.of(node)) {
          if (row.index() > 0) {
            matchRow(node, row, matches);
          }
        }
      }
    }
    return List.copyOf(matches);
  }

  private void matchRow(
      final SyntaxNode comment, final JavadocRows.Row row, final List<RuleMatch> matches) {
    final int lead = row.lead();
    if (lead >= row.text().length()) {
      return;
    }
    final boolean asterisk = row.text().charAt(lead) == ASTERISK;
    final boolean flagged =
        switch (mode) {
          case ALIGN -> asterisk && lead + 1 != comment.span().startColumn() + 1;
          case MISSING -> !asterisk;
          default -> asterisk && missingSpace(row.text(), lead);
        };
    if (flagged) {
      matches.add(CheckSupport.at(row.at(mode == Mode.WHITESPACE ? lead + 1 : lead), message));
    }
  }

  private static boolean missingSpace(final String text, final int asterisk) {
    final int next = asterisk + 1;
    return next < text.length()
        && !Character.isWhitespace(text.charAt(next))
        && text.charAt(next) != SLASH;
  }
}
