package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reports a comment that directly follows another comment in a way that ktlint's {@code
 * no-consecutive-comments} rule disallows: KDoc after KDoc, anything after a KDoc, block comments
 * after block comments, and mixes of block and end-of-line comments that are not separated by a
 * blank line. Consecutive end-of-line comments are allowed.
 */
public final class KotlinConsecutiveCommentsCheck implements RuleCheck {

  private static final char NEWLINE = '\n';
  private static final int BLANK_LINE_NEWLINES = 2;
  private static final String KDOC = "kdoc";
  private static final String BLOCK = "block";
  private static final String EOL = "eol";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the second comment
   */
  public KotlinConsecutiveCommentsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    Optional<SyntaxNode> previous = Optional.empty();
    int newlines = 0;
    for (final SyntaxNode token : KotlinLeaves.tokens(root)) {
      if (KotlinLeaves.isComment(token)) {
        final int gap = newlines;
        if (previous.filter(before -> isDisallowed(before, token, gap)).isPresent()) {
          matches.add(CheckSupport.at(token.span(), message));
        }
        previous = Optional.of(token);
        newlines = 0;
      } else if (KotlinLeaves.NEWLINE.equals(token.kind())) {
        newlines += countNewlines(KotlinLeaves.text(token));
      } else {
        previous = Optional.empty();
      }
    }
    return List.copyOf(matches);
  }

  private static boolean isDisallowed(
      final SyntaxNode previous, final SyntaxNode current, final int newlines) {
    final String before = KotlinLeaves.commentType(previous);
    final String after = KotlinLeaves.commentType(current);
    if (KDOC.equals(before) || BLOCK.equals(before) && BLOCK.equals(after)) {
      return true;
    }
    if (EOL.equals(before) && EOL.equals(after)) {
      return false;
    }
    return before.equals(after) || newlines < BLANK_LINE_NEWLINES;
  }

  private static int countNewlines(final String text) {
    return (int) text.chars().filter(character -> character == NEWLINE).count();
  }
}
