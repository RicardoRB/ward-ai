package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code str.format} call on a string literal that passes the same plain name as more
 * than one positional argument, like Pylint's {@code duplicate-string-formatting-argument}: the
 * name should be passed once as a named argument. Keyword and starred arguments are not counted.
 */
public final class DuplicateFormatArgumentCheck implements RuleCheck {

  private static final Set<String> CALLS = Set.of("atom_expr");
  private static final Pattern LITERAL_FORMAT =
      Pattern.compile("^(\"[^\"]*\"|'[^']*')\\.format\\(.*");
  private static final Pattern NAME = Pattern.compile("^\\w+$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public DuplicateFormatArgumentCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, CALLS)) {
      final String text = SyntaxWalk.text(visit.node());
      if (LITERAL_FORMAT.matcher(text).matches() && repeatsName(text)) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** Whether a positional plain name is passed more than once to the first call of the text. */
  static boolean repeatsName(final String text) {
    final int open = text.indexOf(".format(") + ".format".length();
    int depth = 0;
    int close = -1;
    for (int index = open; index < text.length(); index++) {
      final char c = text.charAt(index);
      if (c == '(') {
        depth++;
      } else if (c == ')' && --depth == 0) {
        close = index;
        break;
      }
    }
    if (close < 0) {
      return false;
    }
    final Map<String, Integer> counts = new HashMap<>();
    for (final String argument :
        ThreadInstantiationCheck.topLevelArguments(text.substring(open + 1, close))) {
      if (NAME.matcher(argument).matches() && counts.merge(argument, 1, Integer::sum) > 1) {
        return true;
      }
    }
    return false;
  }
}
