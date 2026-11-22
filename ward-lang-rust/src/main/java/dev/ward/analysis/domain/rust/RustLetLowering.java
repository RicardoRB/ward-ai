package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lowers a {@code let} statement. An initializer that is a block, {@code if}, {@code match} or loop
 * is lowered as control flow and the pattern is the step that follows; the {@code else} block of a
 * {@code let}-{@code else} runs on its own path and does not fall through.
 */
final class RustLetLowering {
  private final ControlFlowBuilder flow;
  private final RustJumpLowering jumps;
  private final Consumer<SyntaxNode> blocks;
  private final Consumer<SyntaxNode> expressions;

  /**
   * Creates the lowering of the {@code let} statements of one body.
   *
   * @param flow the builder of the body
   * @param jumps the lowering of jumps, for a {@code ?} in the initializer
   * @param blocks lowers the statements of a block
   * @param expressions lowers an expression of the body
   */
  RustLetLowering(
      final ControlFlowBuilder flow,
      final RustJumpLowering jumps,
      final Consumer<SyntaxNode> blocks,
      final Consumer<SyntaxNode> expressions
  ) {
    this.flow = flow;
    this.jumps = jumps;
    this.blocks = blocks;
    this.expressions = expressions;
  }

  /** Lowers a {@code letStatement}. */
  void letStatement(final SyntaxNode node) {
    final SyntaxNode value = valueOf(node);
    final SyntaxNode otherwise = RustTrees.child(node, RustKinds.BLOCK);
    final boolean control = value != null && isControl(value);
    if (control) {
      expressions.accept(value);
    }
    flow.add(control ? RustTrees.child(node, RustKinds.PATTERN_NO_ALT) : node, false);
    jumps.propagates(value);
    if (otherwise != null) {
      diverge(otherwise);
    }
  }

  /** The initializer of a {@code let}: the expression after {@code =}, or null. */
  private static SyntaxNode valueOf(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (RustTrees.isToken(parts.get(index), RustKinds.EQUALS)) {
        return parts.get(index + 1);
      }
    }
    return null;
  }

  private static boolean isControl(final SyntaxNode value) {
    return value.children().size() == 1
        && RustKinds.WITH_BLOCK.equals(value.children().get(0).kind());
  }

  /** The {@code else} block of a {@code let}-{@code else}, which does not fall through. */
  private void diverge(final SyntaxNode otherwise) {
    final int ok = flow.newBlock();
    final int bad = flow.newBlock();
    flow.branch(ok, bad);
    flow.moveTo(bad);
    blocks.accept(otherwise);
    flow.moveTo(ok);
  }
}
