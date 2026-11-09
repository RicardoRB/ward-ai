package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reports a class that derives from a class of the same module in a way the base does not allow: a
 * constructor that never calls the base constructor, an override with fewer defaults than the base
 * method, or a subclass of a class decorated with {@code final}. A base that is imported, unknown,
 * or declared twice is not checked.
 */
public final class ClassHierarchyCheck implements RuleCheck {

  /** The problem a class is reported for. */
  public enum Problem {
    /** A constructor does not call the constructor of its base class. */
    SUPER_INIT_NOT_CALLED,
    /** An override has fewer default values than the method it overrides. */
    SIGNATURE_DIFFERS,
    /** A class derives from a class decorated with {@code final}. */
    SUBCLASSED_FINAL
  }

  private static final String INIT = "__init__";
  private static final String FINAL_DECORATOR = "(?s)^@(typing\\.)?final(?!\\w).*";
  private static final String FUNCTION = "funcdef";
  private static final String CLASS = "classdef";

  private final Problem problem;
  private final String message;

  /**
   * Creates the check.
   *
   * @param problem the problem to report
   * @param message the message of each finding
   */
  public ClassHierarchyCheck(final Problem problem, final String message) {
    this.problem = Objects.requireNonNull(problem, "problem");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final Map<String, SyntaxNode> definitions = ModuleDefinitions.of(root);
    final Set<String> finals = problem == Problem.SUBCLASSED_FINAL ? finalClasses(root) : Set.of();
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxNode child : definitions.values()) {
      if (CLASS.equals(child.kind())) {
        ModuleDefinitions.singleBase(child).ifPresent(baseName -> compare(child, baseName, definitions, finals, found));
      }
    }
    return List.copyOf(found);
  }

  private void compare(
      final SyntaxNode child,
      final String baseName,
      final Map<String, SyntaxNode> definitions,
      final Set<String> finals,
      final List<RuleMatch> found) {
    if (problem == Problem.SUBCLASSED_FINAL) {
      if (finals.contains(baseName)) {
        found.add(new RuleMatch(child.span(), message));
      }
      return;
    }
    final SyntaxNode base = definitions.get(baseName);
    if (base == null || !CLASS.equals(base.kind())) {
      return;
    }
    if (problem == Problem.SUPER_INIT_NOT_CALLED) {
      superInit(child, base, found);
    } else {
      signatures(child, base, found);
    }
  }

  private void superInit(final SyntaxNode child, final SyntaxNode base, final List<RuleMatch> found) {
    final Optional<SyntaxNode> init = ModuleDefinitions.method(child, INIT);
    if (init.isPresent()
        && ModuleDefinitions.method(base, INIT).isPresent()
        && !SyntaxWalk.text(body(init.get())).contains(INIT + "(")) {
      found.add(new RuleMatch(init.get().span(), message));
    }
  }

  private void signatures(final SyntaxNode child, final SyntaxNode base, final List<RuleMatch> found) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(child, Set.of(FUNCTION))) {
      if (visit.ancestors().stream().anyMatch(ancestor -> FUNCTION.equals(ancestor.kind()))) {
        continue;
      }
      final String name = ModuleDefinitions.definedName(visit.node()).orElse("");
      final Optional<SyntaxNode> overridden = ModuleDefinitions.method(base, name);
      if (overridden.isPresent() && fewerDefaults(visit.node(), overridden.get())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
  }

  /** The same positional names, no varargs, and fewer defaults than the overridden method. */
  private static boolean fewerDefaults(final SyntaxNode method, final SyntaxNode overridden) {
    final Parameters mine = Parameters.of(method);
    final Parameters theirs = Parameters.of(overridden);
    return mine.positional().equals(theirs.positional())
        && !mine.varargs()
        && mine.defaults() < theirs.defaults();
  }

  /** The names of top-level classes that a {@code final} decorator marks. */
  private static Set<String> finalClasses(final SyntaxNode root) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(CLASS))) {
      final List<SyntaxNode> ancestors = visit.ancestors();
      if (!ancestors.isEmpty()) {
        final SyntaxNode parent = ancestors.get(ancestors.size() - 1);
        if ("decorated".equals(parent.kind()) && SyntaxWalk.text(parent).matches(FINAL_DECORATOR)) {
          ModuleDefinitions.definedName(visit.node()).ifPresent(names::add);
        }
      }
    }
    return names;
  }

  private static SyntaxNode body(final SyntaxNode definition) {
    return definition.children().get(definition.children().size() - 1);
  }
}
