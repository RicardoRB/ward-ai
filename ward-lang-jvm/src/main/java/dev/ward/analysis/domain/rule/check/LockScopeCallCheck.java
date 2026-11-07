package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each expression whose text matches a pattern when it is, or is not, inside a lock (a
 * {@code synchronized} statement or method) and inside a loop. The check reads the nearest method
 * only in the sense that it looks at every ancestor, so a lambda or a nested class counts as part
 * of the lock or loop around it.
 */
public final class LockScopeCallCheck implements UnitCheck {

  /** Whether a context must, must not or may surround the call. */
  public enum Presence {
    /** The context may or may not be there. */
    ANY,
    /** The context must surround the call. */
    YES,
    /** The context must not surround the call. */
    NO;

    boolean admits(final boolean present) {
      return this == ANY || present == (this == YES);
    }
  }

  private static final Set<String> LOOPS = Set.of("'for'", "'while'", "'do'");
  private static final String STATEMENT = "statement";
  private static final String SYNCHRONIZED = "'synchronized'";
  private static final String MEMBER = "classBodyDeclaration";
  private static final String MODIFIER = "modifier";

  private final Pattern call;
  private final Presence lock;
  private final Presence loop;
  private final String message;

  /**
   * Creates the check.
   *
   * @param call the pattern that the text of the expression must contain a match of
   * @param lock whether a lock must surround the call
   * @param loop whether a loop must surround the call
   * @param message the message
   */
  public LockScopeCallCheck(
      final Pattern call, final Presence lock, final Presence loop, final String message) {
    this.call = Objects.requireNonNull(call, "call");
    this.lock = Objects.requireNonNull(lock, "lock");
    this.loop = Objects.requireNonNull(loop, "loop");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of("expression")).stream()
        .filter(visit -> call.matcher(SyntaxWalk.text(visit.node())).find())
        .filter(visit -> lock.admits(locked(visit.ancestors())))
        .filter(visit -> loop.admits(looped(visit.ancestors())))
        .map(visit -> CheckSupport.at(visit.node().span(), message))
        .toList();
  }

  private static boolean locked(final List<SyntaxNode> ancestors) {
    return ancestors.stream().anyMatch(a -> isStatementOf(a, Set.of(SYNCHRONIZED)) || isSync(a));
  }

  private static boolean looped(final List<SyntaxNode> ancestors) {
    return ancestors.stream().anyMatch(a -> isStatementOf(a, LOOPS));
  }

  private static boolean isStatementOf(final SyntaxNode node, final Set<String> keywords) {
    return STATEMENT.equals(node.kind())
        && node.children().stream().findFirst().map(c -> keywords.contains(c.kind())).orElse(false);
  }

  private static boolean isSync(final SyntaxNode node) {
    return MEMBER.equals(node.kind())
        && node.children().stream()
            .anyMatch(c -> MODIFIER.equals(c.kind()) && "synchronized".equals(SyntaxWalk.text(c)));
  }
}
