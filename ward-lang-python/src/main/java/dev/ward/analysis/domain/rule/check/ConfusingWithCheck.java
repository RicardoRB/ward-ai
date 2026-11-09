package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports a {@code with} statement whose item binds a name with {@code as} and is followed by an
 * item that is not a call and binds nothing, like Pylint's {@code confusing-with-statement}: the
 * pair reads as a tuple. A following call is not reported, since no name could be mistaken for it.
 */
public final class ConfusingWithCheck implements RuleCheck {

  private static final String WITH = "with_stmt";
  private static final String ITEM = "with_item";
  private static final String AS = "as";
  private static final Pattern CALL = Pattern.compile("^[\\w.]+\\(.*\\)$");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ConfusingWithCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(WITH))) {
      final List<SyntaxNode> items =
          visit.node().children().stream().filter(child -> ITEM.equals(child.kind())).toList();
      for (int index = 0; index + 1 < items.size(); index++) {
        if (binds(items.get(index))
            && !binds(items.get(index + 1))
            && !isCall(items.get(index + 1))) {
          found.add(new RuleMatch(visit.node().span(), message));
          break;
        }
      }
    }
    return List.copyOf(found);
  }

  /** Whether a with item has an {@code as} target. */
  private static boolean binds(final SyntaxNode item) {
    return item.children().stream().anyMatch(child -> AS.equals(SyntaxWalk.text(child)));
  }

  private static boolean isCall(final SyntaxNode item) {
    return CALL.matcher(SyntaxWalk.text(item)).matches();
  }
}
