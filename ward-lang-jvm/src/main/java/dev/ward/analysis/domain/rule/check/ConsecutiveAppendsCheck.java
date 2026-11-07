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
 * Reports each statement that appends to a builder when the next statement of the same block
 * appends to the same builder, as PMD's ConsecutiveAppendsShouldReuse does: the appends should be
 * chained on one statement.
 *
 * <p>Shortcut: a statement is a single call on a plain variable name, and the arguments are read as
 * text without their types.
 */
public final class ConsecutiveAppendsCheck implements UnitCheck {

  private static final String BLOCK = "block";
  private static final Pattern APPEND =
      Pattern.compile("^(\\w+)\\.append\\((?:[^()]|\\([^()]*\\))*\\);$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ConsecutiveAppendsCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(BLOCK))) {
      final List<SyntaxNode> statements = statements(visit.node());
      for (int index = 0; index + 1 < statements.size(); index++) {
        if (sameBuilder(statements.get(index), statements.get(index + 1))) {
          matches.add(CheckSupport.at(statements.get(index).span(), message));
        }
      }
    }
    return List.copyOf(matches);
  }

  private static List<SyntaxNode> statements(final SyntaxNode block) {
    return block.children().stream().filter(node -> !node.kind().startsWith("'")).toList();
  }

  private static boolean sameBuilder(final SyntaxNode first, final SyntaxNode second) {
    final Matcher one = APPEND.matcher(SyntaxWalk.text(first));
    final Matcher two = APPEND.matcher(SyntaxWalk.text(second));
    return one.matches() && two.matches() && one.group(1).equals(two.group(1));
  }
}
