package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fails each anonymous class whose body spans more lines than the limit. The body is the class body
 * of a class creation, so a named nested class is not measured.
 */
public final class AnonymousLinesCheck implements RuleCheck {

  private static final String CREATION = "classCreatorRest";
  private static final String BODY = "classBody";

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest allowed line count of the body
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public AnonymousLinesCheck(final int max, final String message) {
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CREATION))) {
      visit.node().children().stream()
          .filter(child -> BODY.equals(child.kind()))
          .forEach(body -> measure(body, matches));
    }
    return List.copyOf(matches);
  }

  private void measure(final SyntaxNode body, final List<RuleMatch> matches) {
    final int lines = body.span().endLine() - body.span().startLine() + 1;
    if (lines > max) {
      matches.add(
          new RuleMatch(
              body.span(),
              CheckSupport.fill(message, String.valueOf(lines), String.valueOf(max))));
    }
  }
}
