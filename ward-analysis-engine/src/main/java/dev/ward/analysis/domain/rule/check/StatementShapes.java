package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the shape of Python blocks: statement counts and the keywords a statement carries. */
final class StatementShapes {

  private static final String BLOCK = "block";
  private static final String SIMPLE_STMTS = "simple_stmts";
  private static final String SIMPLE_STMT = "simple_stmt";
  private static final String STMT = "stmt";
  private static final String IF_STMT = "if_stmt";
  private static final String TEXT = "text";

  private StatementShapes() {}

  /** Returns the statements of a {@code block}, each a {@code simple_stmt} or a {@code stmt}. */
  static List<SyntaxNode> statements(final SyntaxNode block) {
    final List<SyntaxNode> out = new ArrayList<>();
    for (final SyntaxNode child : block.children()) {
      if (SIMPLE_STMTS.equals(child.kind())) {
        child.children().stream().filter(c -> SIMPLE_STMT.equals(c.kind())).forEach(out::add);
      } else if (STMT.equals(child.kind())) {
        out.add(child);
      }
    }
    return out;
  }

  /** Returns the {@code if_stmt} that is the only statement of {@code block}, if there is one. */
  static Optional<SyntaxNode> soleIf(final SyntaxNode block) {
    final List<SyntaxNode> statements = statements(block);
    if (statements.size() != 1) {
      return Optional.empty();
    }
    SyntaxNode node = statements.get(0);
    while (!IF_STMT.equals(node.kind()) && node.children().size() == 1) {
      node = node.children().get(0);
    }
    return IF_STMT.equals(node.kind()) ? Optional.of(node) : Optional.empty();
  }

  /** Returns whether a direct child of {@code node} is the keyword token {@code keyword}. */
  static boolean hasKeyword(final SyntaxNode node, final String keyword) {
    return node.children().stream()
        .anyMatch(c -> c.children().isEmpty() && keyword.equals(c.fields().get(TEXT)));
  }

  /** Returns the blocks that are direct children of {@code node}, in source order. */
  static List<SyntaxNode> blocks(final SyntaxNode node) {
    return node.children().stream().filter(c -> BLOCK.equals(c.kind())).toList();
  }
}
