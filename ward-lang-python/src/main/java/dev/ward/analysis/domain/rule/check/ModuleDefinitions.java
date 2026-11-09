package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The functions and classes that a module declares at its top level, by name. A name declared more
 * than once is left out, and so is a definition under a decorator, because its binding is not known
 * from the syntax alone.
 */
final class ModuleDefinitions {

  private static final String OPEN = "(";
  private static final String IDENTIFIER = "[A-Za-z_]\\w*";
  private static final Set<String> WRAPPERS = Set.of("file_input", "stmt", "compound_stmt");
  private static final Set<String> DEFINITIONS = Set.of("funcdef", "classdef");

  private ModuleDefinitions() {}

  /** Maps each top-level function or class name to its definition, for names declared once. */
  static Map<String, SyntaxNode> of(final SyntaxNode root) {
    final Map<String, SyntaxNode> found = new HashMap<>();
    final Set<String> repeated = new HashSet<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, DEFINITIONS)) {
      final Optional<String> name = definedName(visit.node());
      if (name.isPresent() && onlyWrappers(visit.ancestors())) {
        final SyntaxNode previous = found.put(name.get(), visit.node());
        if (previous != null) {
          repeated.add(name.get());
        }
      }
    }
    repeated.forEach(found::remove);
    return found;
  }

  /** The name a function or class definition declares. */
  static Optional<String> definedName(final SyntaxNode definition) {
    return definition.children().stream()
        .filter(child -> "name".equals(child.kind()))
        .findFirst()
        .map(SyntaxWalk::text);
  }

  /** Reports whether a class statement has a parenthesized base list. */
  static boolean hasBases(final SyntaxNode classDefinition) {
    return classDefinition.children().stream()
        .anyMatch(child -> OPEN.equals(SyntaxWalk.text(child)));
  }

  /** The single base class a class names by a plain name, as in {@code class C(B):}. */
  static Optional<String> singleBase(final SyntaxNode classDefinition) {
    final List<SyntaxNode> children = classDefinition.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (OPEN.equals(SyntaxWalk.text(children.get(index)))) {
        final String inside = SyntaxWalk.text(children.get(index + 1));
        return inside.matches(IDENTIFIER) ? Optional.of(inside) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  /** The direct undecorated method with this name in a class body, if there is exactly one. */
  static Optional<SyntaxNode> method(final SyntaxNode classDefinition, final String name) {
    final List<SyntaxNode> found = new java.util.ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(classDefinition, Set.of("funcdef"))) {
      if (name.equals(definedName(visit.node()).orElse(""))) {
        found.add(visit.node());
      }
    }
    return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
  }

  private static boolean onlyWrappers(final List<SyntaxNode> ancestors) {
    return ancestors.stream().allMatch(ancestor -> WRAPPERS.contains(ancestor.kind()));
  }
}
