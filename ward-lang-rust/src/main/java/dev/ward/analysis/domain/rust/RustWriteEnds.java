package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The end offset of the {@code let}, assignment, {@code for} header or {@code let} condition that
 * writes each name, by the start offset of the name, so a write is ordered after the reads of its
 * value.
 */
final class RustWriteEnds {
  /** An operand, an operator and an operand. */
  private static final int OPERATION = 3;

  private RustWriteEnds() {}

  /** Collects the write ends of everything under {@code tree}. */
  static Map<Integer, Integer> of(final SyntaxNode tree) {
    final Map<Integer, Integer> ends = new HashMap<>();
    collect(tree, ends);
    return ends;
  }

  private static void collect(final SyntaxNode node, final Map<Integer, Integer> ends) {
    switch (node.kind()) {
      case "letStatement" ->
          names(RustTrees.child(node, RustKinds.PATTERN_NO_ALT), node.span().endOffset(), ends);
      case "iteratorLoopExpression" -> forLoop(node, ends);
      case "expression" -> expression(node, ends);
      default -> {
        // Other nodes write nothing.
      }
    }
    node.children().forEach(child -> collect(child, ends));
  }

  private static void forLoop(final SyntaxNode node, final Map<Integer, Integer> ends) {
    final SyntaxNode iterable = RustTrees.child(node, "expression");
    if (iterable != null) {
      names(RustTrees.child(node, "pattern"), iterable.span().endOffset(), ends);
    }
  }

  private static void expression(final SyntaxNode node, final Map<Integer, Integer> ends) {
    final List<SyntaxNode> parts = node.children();
    if (!parts.isEmpty() && RustTrees.isToken(parts.get(0), RustKinds.LET)) {
      names(RustTrees.child(node, "pattern"), node.span().endOffset(), ends);
    } else if (parts.size() == OPERATION && isAssignment(parts.get(1))) {
      final SyntaxNode name = RustTrees.find(parts.get(0), "identifier");
      if (name != null && parts.get(0).children().size() == 1) {
        ends.put(name.span().startOffset(), node.span().endOffset());
      }
    }
  }

  private static boolean isAssignment(final SyntaxNode operator) {
    return RustTrees.isToken(operator, RustKinds.EQUALS) || RustKinds.COMPOUND_ASSIGN.equals(
        operator.kind()
    );
  }

  private static void names(
      final SyntaxNode pattern,
      final int end,
      final Map<Integer,
      Integer> ends
  ) {
    if (pattern == null) {
      return;
    }
    if (RustKinds.IDENTIFIER_PATTERN.equals(pattern.kind())) {
      final SyntaxNode name = RustTrees.child(pattern, "identifier");
      ends.put(name.span().startOffset(), end);
    }
    pattern.children().forEach(child -> names(child, end, ends));
  }
}
