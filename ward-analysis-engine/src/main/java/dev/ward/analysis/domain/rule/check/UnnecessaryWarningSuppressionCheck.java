package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reports each suppression: a {@code @SuppressWarnings} annotation and a comment that names PMD's
 * {@code NOPMD} marker. PMD reports only suppressions that suppress nothing, which needs the
 * results of the other rules. Ward allows no suppression, so every one is reported.
 */
public final class UnnecessaryWarningSuppressionCheck implements UnitCheck {

  private static final String SUPPRESS = "SuppressWarnings";
  private static final String NO_PMD = "NOPMD";
  private static final String COMMENT = "comment";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the suppression text
   */
  public UnnecessaryWarningSuppressionCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      final String text = SyntaxWalk.text(node);
      if ("annotation".equals(node.kind()) && text.contains(SUPPRESS)) {
        matches.add(CheckSupport.at(node.span(), CheckSupport.fill(message, text, "")));
      } else if (COMMENT.equals(node.kind()) && text.contains(NO_PMD)) {
        matches.add(CheckSupport.at(node.span(), CheckSupport.fill(message, text, "")));
      }
    }
    return List.copyOf(matches);
  }
}
