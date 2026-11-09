package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a parameter of a plain function that its body never mentions ({@code unused-argument}),
 * and a method of a class whose name is private to the class and never mentioned again in the
 * class ({@code unused-private-member}). Methods and decorated functions are not checked for
 * arguments, because their signature may be fixed by a base class or a decorator. Names that start
 * with {@code _}, {@code ignored_} or {@code unused_} are ignored, as in Pylint's defaults.
 */
public final class UnusedNameCheck implements RuleCheck {

  /** The problem a name is reported for. */
  public enum Problem {
    /** A parameter the function body never uses. */
    UNUSED_ARGUMENT,
    /** A private method the class never uses. */
    UNUSED_PRIVATE_MEMBER
  }

  private static final String FUNCTION = "funcdef";
  private static final String CLASS = "classdef";
  private static final String DECORATED = "decorated";
  private static final int SINGLE_MENTION = 1;
  private static final String IGNORED = "(?:_.*|ignored_.*|unused_.*)";

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the problem to report
   * @param message the message of each finding
   */
  public UnusedNameCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    if (problem == Problem.UNUSED_ARGUMENT) {
      arguments(root, found);
    } else {
      privateMembers(root, found);
    }
    return List.copyOf(found);
  }

  private void arguments(final SyntaxNode root, final List<RuleMatch> found) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(FUNCTION))) {
      if (visit.ancestors().stream().anyMatch(a -> CLASS.equals(a.kind()) || DECORATED.equals(a.kind()))) {
        continue;
      }
      final SyntaxNode function = visit.node();
      final Set<String> used = usedNames(function);
      for (final SyntaxNode name : Parameters.nameNodes(function)) {
        final String text = SyntaxWalk.text(name);
        if (!used.contains(text) && !text.matches(IGNORED)) {
          found.add(new RuleMatch(name.span(), message));
        }
      }
    }
  }

  /** The texts of the leaves of a function body. */
  private static Set<String> usedNames(final SyntaxNode function) {
    final Set<String> used = new HashSet<>();
    SyntaxWalk.leaves(function.children().get(function.children().size() - 1))
        .forEach(leaf -> used.add(SyntaxWalk.text(leaf)));
    return used;
  }

  private void privateMembers(final SyntaxNode root, final List<RuleMatch> found) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS))) {
      final SyntaxNode definition = visit.node();
      for (final SyntaxWalk.Visit method : SyntaxWalk.visits(definition, Set.of(FUNCTION))) {
        if (directMethod(method.ancestors())) {
          privateMethod(definition, method.node(), found);
        }
      }
    }
  }

  /** Reports whether a function sits in the class body without another function or class between. */
  private static boolean directMethod(final List<SyntaxNode> ancestors) {
    return ancestors.stream()
        .skip(1)
        .noneMatch(a -> FUNCTION.equals(a.kind()) || CLASS.equals(a.kind()) || DECORATED.equals(a.kind()));
  }

  private void privateMethod(final SyntaxNode definition, final SyntaxNode method, final List<RuleMatch> found) {
    final String name = ModuleDefinitions.definedName(method).orElse("");
    if (name.startsWith("__") && !name.endsWith("__")) {
      final long mentions = SyntaxWalk.leaves(definition).stream()
          .filter(leaf -> name.equals(SyntaxWalk.text(leaf)))
          .count();
      if (mentions <= SINGLE_MENTION) {
        found.add(new RuleMatch(method.span(), message));
      }
    }
  }
}
