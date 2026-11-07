package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a bare {@code except:} that comes before another handler of the same {@code try}, like
 * pyflakes {@code F707}: the later handler can never run.
 */
public final class ExceptOrderCheck implements RuleCheck {

  private static final Set<String> TRIES = Set.of("try_stmt");
  private static final String EXCEPT = "except_clause";
  private static final String BARE = "except";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public ExceptOrderCheck(final String message) {
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TRIES)) {
      bareBeforeHandler(visit.node()).ifPresent(span -> found.add(new RuleMatch(span, message)));
    }
    return List.copyOf(found);
  }

  /** The span of the first bare handler that a later handler follows, if any. */
  private static Optional<Span> bareBeforeHandler(final SyntaxNode node) {
    Span bare = null;
    for (final SyntaxNode child : node.children()) {
      if (EXCEPT.equals(child.kind()) && BARE.equals(SyntaxWalk.text(child)) && bare == null) {
        bare = child.span();
      } else if (EXCEPT.equals(child.kind()) && bare != null) {
        return Optional.of(bare);
      }
    }
    return Optional.empty();
  }
}
