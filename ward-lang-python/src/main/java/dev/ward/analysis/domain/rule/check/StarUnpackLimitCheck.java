package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an assignment with more than 256 targets before a starred target, like Ruff's {@code
 * F621} ({@code expressions-in-star-assignment}): Python allows at most {@code 1 << 8} expressions
 * before a starred expression in an assignment target.
 */
public final class StarUnpackLimitCheck implements RuleCheck {

  private static final Set<String> STATEMENTS = Set.of("expr_stmt");
  private static final int MAX_BEFORE_STAR = 1 << 8;
  private static final String ASSIGN = "=";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public StarUnpackLimitCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, STATEMENTS)) {
      final SyntaxNode statement = visit.node();
      if (statement.children().stream().anyMatch(child -> ASSIGN.equals(SyntaxWalk.text(child)))
          && tooManyBeforeStar(SyntaxWalk.text(statement.children().get(0)))) {
        found.add(new RuleMatch(statement.span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** Whether the target lists more than the allowed expressions before its starred one. */
  static boolean tooManyBeforeStar(final String target) {
    final List<String> parts = ThreadInstantiationCheck.topLevelArguments(target);
    for (int index = 0; index < parts.size(); index++) {
      if (parts.get(index).startsWith("*")) {
        return index > MAX_BEFORE_STAR;
      }
    }
    return false;
  }
}
