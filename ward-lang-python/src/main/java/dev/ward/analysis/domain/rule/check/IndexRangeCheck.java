package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports an integer subscript of a list or tuple display written in place, such as
 * {@code [1, 2][5]}, when the subscript is past the end of the display. Names bound to a display are
 * not followed, as Pylint does not report them, and a parenthesized expression without a comma is not
 * a tuple, so it is not checked.
 */
public final class IndexRangeCheck implements RuleCheck {

  private static final Pattern SUBSCRIPT =
      Pattern.compile("^(\\[|\\()([^\\[\\](){}*]*)(\\]|\\))\\[(-?\\d+)\\]$");
  private static final String ATOM_EXPR = "atom_expr";
  private static final String COMMA = ",";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public IndexRangeCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ATOM_EXPR))) {
      final Matcher matcher = SUBSCRIPT.matcher(SyntaxWalk.text(visit.node()).strip());
      if (matcher.matches() && isSequence(matcher.group(1), matcher.group(2))) {
        final int length = Elements.of(matcher.group(2)).size();
        final long index = Long.parseLong(matcher.group(4));
        if (index >= length || index < -length) {
          found.add(new RuleMatch(visit.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  /** Reports whether the brackets are a list, or a tuple, which needs a comma unless it is empty. */
  private static boolean isSequence(final String open, final String inside) {
    return "[".equals(open) || inside.isBlank() || inside.contains(COMMA);
  }
}
