package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.function.Consumer;

/** Lowers the {@code if}, {@code match} and labeled block expressions of one Rust body. */
final class RustBranchLowering {
  /** The arm, its arrow and the expression after it. */
  private static final int BODY_AFTER_ARM = 2;

  private final ControlFlowBuilder flow;
  private final Consumer<SyntaxNode> blocks;
  private final Consumer<SyntaxNode> expressions;

  /**
   * Creates the lowering of the branches of one body.
   *
   * @param flow the builder of the body
   * @param blocks lowers the statements of a block
   * @param expressions lowers an expression of the body
   */
  RustBranchLowering(
      final ControlFlowBuilder flow,
      final Consumer<SyntaxNode> blocks,
      final Consumer<SyntaxNode> expressions
  ) {
    this.flow = flow;
    this.blocks = blocks;
    this.expressions = expressions;
  }

  /** A block, or a block with a label that a {@code break} can leave. */
  void labeledOrPlain(final SyntaxNode node, final SyntaxNode block) {
    final SyntaxNode label = RustTrees.child(node, "loopLabel");
    if (label == null) {
      blocks.accept(block);
      return;
    }
    final int after = flow.newBlock();
    flow.jumps().pushLabel(RustTrees.text(label).replace(":", "").strip(), after);
    blocks.accept(block);
    flow.jumps().popFrame();
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }

  /** Lowers an {@code ifExpression}, with its {@code else} or {@code else if}. */
  void ifExpression(final SyntaxNode node) {
    final int then = flow.newBlock();
    final int otherwise = flow.newBlock();
    final int after = flow.newBlock();
    flow.add(RustTrees.child(node, RustKinds.EXPRESSION), false);
    flow.branch(then, otherwise);
    flow.moveTo(then);
    blocks.accept(RustTrees.child(node, RustKinds.BLOCK));
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(otherwise);
    elsePart(node);
    flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    flow.moveTo(after);
  }

  private void elsePart(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    for (int index = 0; index + 1 < parts.size(); index++) {
      if (RustTrees.isToken(parts.get(index), RustKinds.ELSE)) {
        final SyntaxNode branch = parts.get(index + 1);
        if (RustKinds.BLOCK.equals(branch.kind())) {
          blocks.accept(branch);
        } else {
          ifExpression(branch);
        }
      }
    }
  }

  /** Lowers a {@code matchExpression}: each arm is a block that the subject reaches. */
  void matchExpression(final SyntaxNode node) {
    flow.add(RustTrees.child(node, RustKinds.EXPRESSION), false);
    final int subject = flow.current();
    final int after = flow.newBlock();
    final SyntaxNode arms = RustTrees.child(node, "matchArms");
    if (arms == null) {
      flow.edge(subject, after, FlowEdgeKind.NORMAL);
    } else {
      arms(arms, subject, after);
    }
    flow.moveTo(after);
  }

  private void arms(final SyntaxNode arms, final int subject, final int after) {
    final List<SyntaxNode> parts = arms.children();
    for (int index = 0; index < parts.size(); index++) {
      if (RustKinds.MATCH_ARM.equals(parts.get(index).kind())) {
        final int arm = flow.newBlock();
        flow.edge(subject, arm, FlowEdgeKind.NORMAL);
        flow.moveTo(arm);
        final SyntaxNode guard = RustTrees.child(parts.get(index), "matchArmGuard");
        if (guard != null) {
          flow.add(guard, false);
        }
        expressions.accept(armBody(parts, index));
        flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
      }
    }
  }

  /** The expression that follows {@code arm =>}; a trailing comma node holds it. */
  private static SyntaxNode armBody(final List<SyntaxNode> parts, final int armIndex) {
    final SyntaxNode body = parts.get(Math.min(armIndex + BODY_AFTER_ARM, parts.size() - 1));
    if (RustKinds.MATCH_ARM_EXPRESSION.equals(body.kind())) {
      return body.children().get(0);
    }
    return body;
  }
}
