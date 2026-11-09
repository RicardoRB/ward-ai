package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a loop with an {@code else} clause and no {@code break} of its own, like Pylint W0120.
 */
public final class UselessElseOnLoopCheck implements RuleCheck {

  private static final Set<String> LOOPS = Set.of("for_stmt", "while_stmt");
  private static final Set<String> BREAKS = Set.of("break_stmt");
  private static final Set<String> LOOP_SKIP =
      Set.of("for_stmt", "while_stmt", "funcdef", "classdef");
  private static final String ELSE = "else";

  private final String message;

  /** Creates the check with the message of each finding. */
  public UselessElseOnLoopCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, LOOPS)) {
      final SyntaxNode loop = visit.node();
      if (hasElse(loop) && FunctionBodies.own(loop, BREAKS, LOOP_SKIP).isEmpty()) {
        found.add(new RuleMatch(loop.span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean hasElse(final SyntaxNode loop) {
    return loop.children().stream()
        .anyMatch(child -> child.children().isEmpty() && ELSE.equals(SyntaxWalk.text(child)));
  }
}
