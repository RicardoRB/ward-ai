package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a {@code return}, {@code break} or {@code continue} inside a {@code finally} block, like
 * {@code B012}: it silences the exception that was propagating. A jump inside a loop or function
 * nested in the block belongs to that loop or function and is not reported.
 */
public final class FinallyJumpCheck implements RuleCheck {

  private static final Set<String> TRIES = Set.of("try_stmt");
  private static final Set<String> JUMPS = Set.of("return_stmt", "break_stmt", "continue_stmt");
  private static final Set<String> LOOPS = Set.of("for_stmt", "while_stmt");
  private static final Set<String> SCOPES =
      Set.of("funcdef", "classdef", "async_funcdef", "lambdef");
  private static final String FINALLY = "finally";
  private static final String BLOCK = "block";
  private static final String BREAK = "break_stmt";
  private static final String CONTINUE = "continue_stmt";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public FinallyJumpCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, TRIES)) {
      finallyBlock(visit.node()).ifPresent(block -> addJumps(block, found));
    }
    return List.copyOf(found);
  }

  private static java.util.Optional<SyntaxNode> finallyBlock(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    for (int index = 0; index < children.size(); index++) {
      if (FINALLY.equals(SyntaxWalk.text(children.get(index))) && index + 1 < children.size()) {
        return children.stream()
            .skip(index + 1)
            .filter(child -> BLOCK.equals(child.kind()))
            .findFirst();
      }
    }
    return java.util.Optional.empty();
  }

  private void addJumps(final SyntaxNode block, final List<RuleMatch> found) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(block, JUMPS)) {
      if (!enclosedBy(visit.ancestors(), jumpBoundaries(visit.node()))) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
  }

  private static Set<String> jumpBoundaries(final SyntaxNode jump) {
    return BREAK.equals(jump.kind()) || CONTINUE.equals(jump.kind())
        ? union(SCOPES, LOOPS)
        : SCOPES;
  }

  private static Set<String> union(final Set<String> first, final Set<String> second) {
    final Set<String> all = new java.util.HashSet<>(first);
    all.addAll(second);
    return Set.copyOf(all);
  }

  private static boolean enclosedBy(final List<SyntaxNode> ancestors, final Set<String> kinds) {
    return ancestors.stream().anyMatch(ancestor -> kinds.contains(ancestor.kind()));
  }
}
