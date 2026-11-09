package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Finds the nodes of a function or class body, leaving nested function and class scopes out. */
final class FunctionBodies {

  /** The kinds that open a scope of their own. */
  static final Set<String> SCOPES = Set.of("funcdef", "classdef");

  private static final Set<String> ASSIGNMENTS = Set.of("expr_stmt", "for_stmt");
  private static final Set<String> TARGETS = Set.of("testlist_star_expr", "exprlist");

  private FunctionBodies() {}

  /** The descendants of {@code scope} of the given kinds, not entering {@code skip} subtrees. */
  static List<SyntaxNode> own(
      final SyntaxNode scope, final Set<String> kinds, final Set<String> skip) {
    final List<SyntaxNode> found = new ArrayList<>();
    collect(scope, kinds, skip, found);
    return found;
  }

  /** The descendants of {@code function} of the given kinds, leaving nested scopes out. */
  static List<SyntaxNode> ownScope(final SyntaxNode function, final Set<String> kinds) {
    return own(function, kinds, SCOPES);
  }

  /** The names that the parameters of {@code function} bind. */
  static Set<String> parameterNames(final SyntaxNode function) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode parameter : ownScope(function, Set.of("tfpdef"))) {
      final String text = SyntaxWalk.text(parameter).replaceAll("[:].*", "");
      if (text.matches("\\w+")) {
        names.add(text);
      }
    }
    return names;
  }

  /**
   * The names that assignments, augmented assignments and loop targets bind in {@code function}.
   */
  static Set<String> assignedNames(final SyntaxNode function) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode statement : ownScope(function, ASSIGNMENTS)) {
      names.addAll(names(target(statement)));
    }
    return names;
  }

  /** The names in a comma-separated target list such as {@code a, (b, c)}. */
  static List<String> names(final String targets) {
    final List<String> names = new ArrayList<>();
    for (final String piece : targets.split(",")) {
      final String bare = piece.replaceAll("^[*(\\[]+|[)\\]]+$", "");
      if (bare.matches("[A-Za-z_]\\w*")) {
        names.add(bare);
      }
    }
    return names;
  }

  /** The first child of {@code node} of the given kind, if there is one. */
  static Optional<SyntaxNode> child(final SyntaxNode node, final String kind) {
    return node.children().stream().filter(child -> kind.equals(child.kind())).findFirst();
  }

  private static String target(final SyntaxNode statement) {
    return statement.children().stream()
        .filter(child -> TARGETS.contains(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text)
        .orElse("");
  }

  private static void collect(
      final SyntaxNode node,
      final Set<String> kinds,
      final Set<String> skip,
      final List<SyntaxNode> found) {
    for (final SyntaxNode child : node.children()) {
      if (kinds.contains(child.kind())) {
        found.add(child);
      }
      if (!skip.contains(child.kind())) {
        collect(child, kinds, skip, found);
      }
    }
  }
}
