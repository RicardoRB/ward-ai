package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Reads names and target lists from the expression nodes of the Python grammar. */
final class PythonNames {

  /** The node kinds that wrap a single operand when they have one child. */
  private static final Set<String> CHAIN =
      Set.of(
          "test",
          "or_test",
          "and_test",
          "not_test",
          "comparison",
          "expr",
          "xor_expr",
          "and_expr",
          "shift_expr",
          "arith_expr",
          "term",
          "factor",
          "power",
          "atom_expr",
          "atom",
          "testlist_star_expr",
          "testlist",
          "exprlist",
          "testlist_comp");

  private static final Set<String> LISTS =
      Set.of("testlist_star_expr", "testlist", "exprlist", "testlist_comp");
  private static final String COMMA = ",";

  /** The kind of a name node. */
  static final String NAME = "name";

  /** The kind of an expression node. */
  static final String TEST = "test";

  private PythonNames() {}

  /** Descends through the nodes that wrap a single operand. */
  static SyntaxNode unwrap(final SyntaxNode node) {
    SyntaxNode current = node;
    while (CHAIN.contains(current.kind()) && current.children().size() == 1) {
      current = current.children().get(0);
    }
    return current;
  }

  /** The {@code name} node that {@code node} is made of alone, if it is a plain name. */
  static Optional<SyntaxNode> plainName(final SyntaxNode node) {
    final SyntaxNode inner = unwrap(node);
    return NAME.equals(inner.kind()) ? Optional.of(inner) : Optional.empty();
  }

  /** Reports whether {@code node} is a comma-separated list of targets or operands. */
  static boolean isList(final SyntaxNode node) {
    return LISTS.contains(node.kind());
  }

  /** The operands of a list node, without the commas. */
  static List<SyntaxNode> elements(final SyntaxNode node) {
    return node.children().stream()
        .filter(child -> !COMMA.equals(PythonSyntax.text(child)))
        .toList();
  }

  /** The text of the identifier a {@code name} node holds. */
  static String identifier(final SyntaxNode name) {
    return PythonSyntax.dottedName(name);
  }

  /** The index of the first part whose text is {@code text}, or -1. */
  static int indexOf(final List<SyntaxNode> parts, final String text) {
    for (int index = 0; index < parts.size(); index++) {
      if (text.equals(PythonSyntax.text(parts.get(index)))) {
        return index;
      }
    }
    return -1;
  }

  /** The child of {@code node} at {@code index}, or empty when it has fewer children. */
  static Optional<SyntaxNode> child(final SyntaxNode node, final int index) {
    return index < node.children().size()
        ? Optional.of(node.children().get(index))
        : Optional.empty();
  }
}
