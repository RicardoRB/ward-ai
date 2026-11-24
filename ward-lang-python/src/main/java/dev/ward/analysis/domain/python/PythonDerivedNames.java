package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.python.PythonTokens.Range;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Finds the names that are made from a parameter by assignments in a body. */
final class PythonDerivedNames {
  private static final Set<String> NESTED = Set.of("funcdef", "classdef", "lambdef", "decorated");

  private PythonDerivedNames() {}

  private static boolean grows(final List<String> assignment, final Set<String> made) {
    return !made.contains(assignment.get(0))
        && PythonTokens.mentions(assignment, new Range(1, assignment.size()), made);
  }

  /** Each {@code name = value} under the node, as the name followed by the tokens of the value. */
  static List<List<String>> assignments(final SyntaxNode node) {
    final List<List<String>> found = new ArrayList<>();
    collect(node, found);
    return found;
  }

  static Set<String> madeFrom(final String parameter, final List<List<String>> assigned) {
    final Set<String> made = new HashSet<>(Set.of(parameter));
    boolean grown = true;
    while (grown) {
      grown = false;
      final int before = made.size();
      assigned.stream().filter(assignment -> grows(assignment, made)).forEach(
          assignment -> made.add(assignment.get(0))
      );
      grown = made.size() > before;
    }
    return made;
  }

  /** Each {@code name = value} as the name followed by the tokens of the value. */
  private static void collect(final SyntaxNode node, final List<List<String>> out) {
    if (NESTED.contains(node.kind())) {
      return;
    }
    final List<SyntaxNode> parts = node.children();
    if ("expr_stmt".equals(node.kind())
        && parts.size() == PythonSyntax.THREE_PARTS
        && PythonSyntax.isToken(parts.get(1), "=")) {
      final List<String> target = PythonLiteralCollections.words(parts.get(0));
      if (target.size() == 1 && target.get(0).matches("[A-Za-z_]\\w*")) {
        out.add(
            Stream.concat(target.stream(), PythonLiteralCollections.words(parts.get(2)).stream())
                .toList()
        );
      }
    }
    parts.forEach(child -> collect(child, out));
  }
}
