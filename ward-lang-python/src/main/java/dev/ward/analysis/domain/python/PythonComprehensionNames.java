package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;

/**
 * The element of a comprehension reads names of the comprehension's own scope: a name its {@code
 * for} clauses bind is not a read of the body that holds the comprehension.
 */
final class PythonComprehensionNames {
  private static final String COMP_FOR = "comp_for";
  private static final String COMP_ITER = "comp_iter";
  private static final String FOR = "for";

  private PythonComprehensionNames() {}

  /** Tells whether {@code node} is an element followed by one {@code for} clause. */
  static boolean isComprehension(final SyntaxNode node) {
    return !PythonSyntax.children(node, COMP_FOR).isEmpty();
  }

  /** Records the reads of the element that the clauses do not bind. */
  static void reads(
      final PythonNameAccesses names,
      final SyntaxNode node,
      final List<VariableAccess> out
  ) {
    final List<VariableAccess> bound = new ArrayList<>();
    clauseTargets(names, PythonSyntax.children(node, COMP_FOR).get(0), bound);
    final List<VariableAccess> elements = new ArrayList<>();
    for (final SyntaxNode child : node.children()) {
      if (!COMP_FOR.equals(child.kind())) {
        names.reads(child, elements);
      }
    }
    elements.stream()
        .filter(read -> bound.stream().noneMatch(target -> target.name().equals(read.name())))
        .forEach(out::add);
  }

  private static void clauseTargets(
      final PythonNameAccesses names,
      final SyntaxNode clause,
      final List<VariableAccess> out
  ) {
    final List<SyntaxNode> parts = clause.children();
    final int target = PythonNames.indexOf(parts, FOR) + 1;
    if (COMP_FOR.equals(clause.kind()) && target > 0) {
      names.writeNames(parts.get(target), out);
    }
    for (final SyntaxNode part : parts) {
      if (COMP_ITER.equals(part.kind())) {
        clauseTargets(names, part.children().get(0), out);
      }
    }
  }
}
