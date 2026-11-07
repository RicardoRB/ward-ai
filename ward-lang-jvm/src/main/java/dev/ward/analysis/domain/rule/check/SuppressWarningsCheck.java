package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code @SuppressWarnings} whose warning names match {@code format}. The default format
 * matches the empty name, so an empty suppression is reported.
 */
public final class SuppressWarningsCheck implements RuleCheck {

  private static final String ANNOTATION = "annotation";
  private static final String NAME = "SuppressWarnings";
  private static final String QUOTE = "\"";

  private final Pattern format;
  private final String message;

  /**
   * Creates the check.
   *
   * @param format the pattern a suppressed warning name must not match
   * @param message the message
   */
  public SuppressWarningsCheck(final Pattern format, final String message) {
    this.format = Objects.requireNonNull(format, "format");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ANNOTATION))) {
      final SyntaxNode annotation = visit.node();
      if (SyntaxWalk.text(annotation).startsWith("@" + NAME) && hasBadName(annotation)) {
        matches.add(CheckSupport.at(annotation.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean hasBadName(final SyntaxNode annotation) {
    return SyntaxWalk.leaves(annotation).stream()
        .map(leaf -> leaf.fields().get("text"))
        .filter(text -> text.startsWith(QUOTE) && text.endsWith(QUOTE) && text.length() >= 2)
        .map(text -> text.substring(1, text.length() - 1))
        .anyMatch(name -> format.matcher(name).find());
  }
}
