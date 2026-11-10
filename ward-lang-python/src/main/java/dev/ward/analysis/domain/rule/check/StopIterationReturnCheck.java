package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Reports {@code raise StopIteration} inside a generator, like Pylint R1708. */
public final class StopIterationReturnCheck implements RuleCheck {

  private static final Set<String> FUNCTIONS = Set.of("funcdef");
  private static final Set<String> YIELDS = Set.of("yield_expr", "yield_stmt");
  private static final Set<String> RAISES = Set.of("raise_stmt");
  private static final Pattern STOP_ITERATION = Pattern.compile("^raiseStopIteration(?!\\w)");

  private final String message;

  /** Creates the check with the message of each finding. */
  public StopIterationReturnCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      final SyntaxNode function = visit.node();
      if (FunctionBodies.ownScope(function, YIELDS).isEmpty()) {
        continue;
      }
      for (final SyntaxNode raise : FunctionBodies.ownScope(function, RAISES)) {
        if (STOP_ITERATION.matcher(SyntaxWalk.text(raise)).find()) {
          found.add(new RuleMatch(raise.span(), message));
        }
      }
    }
    return List.copyOf(found);
  }
}
