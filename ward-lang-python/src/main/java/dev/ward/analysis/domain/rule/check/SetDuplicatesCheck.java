package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each repeated literal in a set display, like {@code B033}: the second and later copies
 * add nothing. A display with a colon is a dictionary, and a comprehension is not judged.
 */
public final class SetDuplicatesCheck implements RuleCheck {

  private static final Set<String> ATOMS = Set.of("atom");
  private static final String OPEN = "{";
  private static final String CLOSE = "}";
  private static final String COLON = ":";
  private static final String FOR = "for";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public SetDuplicatesCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, ATOMS)) {
      final String text = SyntaxWalk.text(visit.node());
      if (isSet(text)) {
        for (int copy = 0; copy < repeats(text); copy++) {
          found.add(new RuleMatch(visit.node().span(), message));
        }
      }
    }
    return List.copyOf(found);
  }

  private static boolean isSet(final String text) {
    return text.startsWith(OPEN)
        && text.endsWith(CLOSE)
        && !text.contains(COLON)
        && !text.contains(FOR)
        && text.length() > 2;
  }

  /** How many later items repeat an earlier literal of the display. */
  private static int repeats(final String text) {
    final Set<String> seen = new HashSet<>();
    int repeats = 0;
    for (final String item : PercentArgument.split(text.substring(1, text.length() - 1), ',')) {
      if (isLiteral(item) && !seen.add(item)) {
        repeats++;
      }
    }
    return repeats;
  }

  private static boolean isLiteral(final String item) {
    return !item.isEmpty()
        && (item.charAt(0) == '"' || item.charAt(0) == '\'' || Character.isDigit(item.charAt(0)));
  }
}
