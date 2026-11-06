package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Scores each method and constructor with its NPath complexity: the number of acyclic execution
 * paths through its body. A method fails when its score is above the limit. The counting rules are
 * in {@link NPathPaths} and {@link NPathBranches}.
 */
public final class NPathCheck implements RuleCheck {

  private static final Set<String> SCOPES = Set.of("methodDeclaration", "constructorDeclaration");
  private static final String BLOCK = "block";
  private static final String METHOD_BODY = "methodBody";
  private static final long NO_BODY = 1L;

  private final int max;
  private final String message;

  /**
   * Creates the check.
   *
   * @param max the largest allowed NPath score
   * @param message the message; {@code {value}} and {@code {max}} are filled in
   * @throws IllegalArgumentException if {@code max} is negative
   */
  public NPathCheck(final int max, final String message) {
    this.max = CheckSupport.nonNegative(max, "max");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, SCOPES)) {
      final SyntaxNode node = visit.node();
      report(node, body(node).map(NPathPaths::block).orElse(NO_BODY), matches);
    }
    return List.copyOf(matches);
  }

  private void report(final SyntaxNode node, final long score, final List<RuleMatch> matches) {
    if (score > max) {
      matches.add(
          new RuleMatch(
              node.span(), CheckSupport.fill(message, String.valueOf(score), String.valueOf(max))));
    }
  }

  private static Optional<SyntaxNode> body(final SyntaxNode scope) {
    for (final SyntaxNode child : scope.children()) {
      if (METHOD_BODY.equals(child.kind())) {
        return child.children().stream().filter(part -> BLOCK.equals(part.kind())).findFirst();
      }
      if (BLOCK.equals(child.kind())) {
        return Optional.of(child);
      }
    }
    return Optional.empty();
  }
}
