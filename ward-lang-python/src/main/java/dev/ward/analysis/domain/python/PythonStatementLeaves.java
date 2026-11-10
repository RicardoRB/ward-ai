package dev.ward.analysis.domain.python;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lists the statements of a block or a file in source order. A statement that holds other
 * statements, such as an {@code if}, is one entry; its body is read from its own block.
 */
public final class PythonStatementLeaves {

  private static final Set<String> SEQUENCES =
      Set.of("file_input", "block", "stmt", "simple_stmts");
  private static final Set<String> WRAPPERS =
      Set.of(
          "simple_stmt", "small_stmt", "flow_stmt", "import_stmt", "compound_stmt", "async_stmt");

  private PythonStatementLeaves() {}

  /**
   * The statements of a block or a file, with the sequences and single-child wrappers looked
   * through.
   *
   * @param node the block or file
   * @return the statements in source order
   */
  public static List<SyntaxNode> leaves(final SyntaxNode node) {
    final List<SyntaxNode> found = new ArrayList<>();
    collect(node, found);
    return found;
  }

  private static void collect(final SyntaxNode node, final List<SyntaxNode> found) {
    final List<SyntaxNode> parts =
        node.children().stream().filter(c -> !c.children().isEmpty()).toList();
    if (SEQUENCES.contains(node.kind())) {
      for (final SyntaxNode part : parts) {
        collect(part, found);
      }
    } else if (WRAPPERS.contains(node.kind()) && parts.size() == 1) {
      collect(parts.get(0), found);
    } else {
      found.add(node);
    }
  }
}
