package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Finds the definitions a stub module or class declares, without looking into function bodies. */
final class PythonStubDefinitions {

  private PythonStubDefinitions() {}

  /** The {@code kind} definitions named {@code name} directly in {@code scope}. */
  static List<SyntaxNode> named(final SyntaxNode scope, final String kind, final String name) {
    final List<SyntaxNode> found = new ArrayList<>();
    collect(scope, found);
    return found.stream()
        .filter(node -> kind.equals(node.kind()))
        .filter(node -> name.equals(PythonNames.identifier(node.children().get(1))))
        .toList();
  }

  /** The base class names written in {@code klass}. */
  static List<String> bases(final SyntaxNode klass) {
    final List<String> bases = new ArrayList<>();
    for (final SyntaxNode arglist : PythonSyntax.children(klass, PythonTypeKinds.ARGLIST)) {
      for (final SyntaxNode argument : PythonSyntax.children(arglist, PythonTypeKinds.ARGUMENT)) {
        bases.add(PythonNames.identifier(argument.children().get(0)));
      }
    }
    return bases;
  }

  /** The only match, or empty when there are none or several. */
  static <T> Optional<T> single(final List<T> matches) {
    return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> found) {
    for (final SyntaxNode child : node.children()) {
      switch (child.kind()) {
        case PythonTypeKinds.FUNCDEF, PythonTypeKinds.CLASSDEF -> found.add(child);
        case PythonTypeKinds.STMT,
            PythonTypeKinds.COMPOUND_STMT,
            PythonTypeKinds.BLOCK,
            PythonTypeKinds.DECORATED ->
            collect(child, found);
        default -> {
          // Other statements declare nothing that a stub member needs.
        }
      }
    }
  }
}
