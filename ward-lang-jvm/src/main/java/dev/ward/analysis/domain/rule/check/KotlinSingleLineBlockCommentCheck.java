package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports a block comment on a single line that ends its line, which ktlint's {@code
 * no-single-line-block-comment} rule asks to write as an end-of-line comment.
 */
public final class KotlinSingleLineBlockCommentCheck implements RuleCheck {

  private static final char NEWLINE = '\n';
  private static final String END_OF_FILE = "EOF";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the comment
   */
  public KotlinSingleLineBlockCommentCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<SyntaxNode> tokens = KotlinLeaves.tokens(root);
    final List<RuleMatch> matches = new ArrayList<>();
    for (int index = 0; index < tokens.size(); index++) {
      final SyntaxNode token = tokens.get(index);
      if (isSingleLineBlock(token) && endsLine(tokens, index)) {
        matches.add(CheckSupport.at(token.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isSingleLineBlock(final SyntaxNode token) {
    return KotlinLeaves.isComment(token)
        && KotlinLeaves.isBlock(token)
        && KotlinLeaves.text(token).indexOf(NEWLINE) < 0;
  }

  private static boolean endsLine(final List<SyntaxNode> tokens, final int index) {
    final int next = index + 1;
    return next == tokens.size()
        || KotlinLeaves.NEWLINE.equals(tokens.get(next).kind())
        || END_OF_FILE.equals(tokens.get(next).kind());
  }
}
