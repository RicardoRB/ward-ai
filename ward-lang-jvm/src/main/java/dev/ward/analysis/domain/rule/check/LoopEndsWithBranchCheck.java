package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a {@code for}, {@code while} or {@code do} loop whose body block ends with a {@code
 * break}, {@code continue} or {@code return} statement, so that the loop never repeats. The
 * decision reads the last statement's syntax node, never its text.
 */
public final class LoopEndsWithBranchCheck implements UnitCheck {

  private static final String STATEMENT = "statement";
  private static final String BLOCK = "block";
  private static final String BLOCK_STATEMENT = "blockStatement";
  private static final Set<String> LOOPS = Set.of("'for'", "'while'", "'do'");
  private static final Set<String> BRANCHES = Set.of("'break'", "'continue'", "'return'");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message reported at the loop
   */
  public LoopEndsWithBranchCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(LoopEndsWithBranchCheck::endsWithBranch)
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  private static boolean endsWithBranch(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (parts.isEmpty() || !LOOPS.contains(parts.get(0).kind())) {
      return false;
    }
    return parts.stream()
        .filter(part -> STATEMENT.equals(part.kind()))
        .findFirst()
        .flatMap(LoopEndsWithBranchCheck::lastStatement)
        .map(last -> BRANCHES.contains(last.children().get(0).kind()))
        .orElse(false);
  }

  private static Optional<SyntaxNode> lastStatement(final SyntaxNode body) {
    final List<SyntaxNode> inner = body.children();
    if (inner.isEmpty() || !BLOCK.equals(inner.get(0).kind())) {
      return Optional.empty();
    }
    final List<SyntaxNode> statements =
        inner.get(0).children().stream().filter(c -> BLOCK_STATEMENT.equals(c.kind())).toList();
    if (statements.isEmpty()) {
      return Optional.empty();
    }
    return statements.get(statements.size() - 1).children().stream()
        .filter(c -> STATEMENT.equals(c.kind()) && !c.children().isEmpty())
        .findFirst();
  }
}
