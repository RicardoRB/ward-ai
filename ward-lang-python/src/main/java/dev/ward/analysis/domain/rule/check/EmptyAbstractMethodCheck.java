package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports an empty method of a class that inherits {@code ABC} and has no decorator, like {@code
 * B027}: its body only passes or is {@code ...}, so it should be abstract.
 */
public final class EmptyAbstractMethodCheck implements RuleCheck {

  private static final Set<String> METHODS = Set.of("funcdef");
  private static final String CLASS = "classdef";
  private static final String DECORATED = "decorated";
  private static final String ABC = "ABC";
  private static final Set<String> EMPTY = Set.of("pass", "...");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public EmptyAbstractMethodCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, METHODS)) {
      if (!decorated(visit) && abstractBase(enclosingClass(visit)) && emptyBody(visit.node())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  private static boolean decorated(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> ancestors = visit.ancestors();
    return !ancestors.isEmpty() && DECORATED.equals(ancestors.get(ancestors.size() - 1).kind());
  }

  private static Optional<SyntaxNode> enclosingClass(final SyntaxWalk.Visit visit) {
    final List<SyntaxNode> ancestors = visit.ancestors();
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      if (CLASS.equals(ancestors.get(index).kind())) {
        return Optional.of(ancestors.get(index));
      }
    }
    return Optional.empty();
  }

  /** Whether a class header names {@code ABC} among its bases. */
  private static boolean abstractBase(final Optional<SyntaxNode> cls) {
    return cls.isPresent()
        && SyntaxWalk.leaves(cls.get()).stream()
            .takeWhile(leaf -> !":".equals(SyntaxWalk.text(leaf)))
            .anyMatch(leaf -> ABC.equals(SyntaxWalk.text(leaf)));
  }

  /** Whether a method's body is nothing but {@code pass} or {@code ...}. */
  private static boolean emptyBody(final SyntaxNode method) {
    final List<String> tokens =
        method.children().stream()
            .filter(child -> "block".equals(child.kind()))
            .flatMap(child -> SyntaxWalk.leaves(child).stream())
            .map(SyntaxWalk::text)
            .filter(text -> !text.isBlank())
            .toList();
    return !tokens.isEmpty() && tokens.stream().allMatch(EMPTY::contains);
  }
}
