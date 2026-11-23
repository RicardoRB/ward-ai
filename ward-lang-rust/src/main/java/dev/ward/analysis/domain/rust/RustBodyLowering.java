package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.ControlFlowGraph;
import dev.ward.analysis.domain.ir.FlowSites;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.List;

/**
 * Lowers the statements of one Rust body into a control-flow graph. A block, {@code if}, {@code
 * match}, loop, labeled block and the jumps are lowered when they are a statement, the tail of a
 * block, an arm, or the initializer of a {@code let}; one nested deeper in an expression, and a
 * closure or an {@code async} block, is part of the step that holds it. A nested function or type
 * is another body. The branches, loops, jumps and {@code let} statements are lowered by their own
 * classes, which share this body's builder. shortcut: the conditions of a let chain are one step
 * that does not short-circuit, and a guard that fails does not fall to the next arm; upgrade by
 * lowering each operand.
 */
final class RustBodyLowering {
  private static final String MACRO_ITEM = "macroItem";
  private static final String LOOP = "loopExpression";

  private final ControlFlowBuilder flow;
  private final RustLoopLowering loops;
  private final RustBranchLowering branches;
  private final RustJumpLowering jumps;
  private final RustLetLowering lets;

  /**
   * Starts the lowering of one body.
   *
   * @param accesses the finder of the variable accesses of the body
   * @param sites the finder of the calls of the body
   */
  RustBodyLowering(final RustAccesses accesses, final FlowSites sites) {
    this.flow = new ControlFlowBuilder(accesses, sites);
    this.jumps = new RustJumpLowering(flow);
    this.loops = new RustLoopLowering(flow, this::block);
    this.branches = new RustBranchLowering(flow, this::block, this::expression);
    this.lets = new RustLetLowering(flow, jumps, this::block, this::expression);
  }

  /**
   * Lowers a body.
   *
   * @param name the name of the body
   * @param entry the parameter accesses, bound at the entry
   * @param body the block of the body
   * @param span where the body is in the source
   * @return the graph of the body
   */
  ControlFlowGraph lower(
      final String name,
      final List<VariableAccess> entry,
      final SyntaxNode body,
      final Span span
  ) {
    flow.record(entry, List.of());
    block(body);
    return flow.build(name, span);
  }

  /**
   * Lowers the body of a closure or an {@code async} block, which is a block or an expression.
   *
   * @param name the name of the body
   * @param entry the parameter accesses, bound at the entry
   * @param body the block or expression of the body
   * @param span where the body is in the source
   * @return the graph of the body
   */
  ControlFlowGraph lowerDeferred(
      final String name,
      final List<VariableAccess> entry,
      final SyntaxNode body,
      final Span span
  ) {
    flow.record(entry, List.of());
    if (RustKinds.BLOCK.equals(body.kind())) {
      block(body);
    } else {
      expression(body);
    }
    return flow.build(name, span);
  }

  private void block(final SyntaxNode block) {
    final SyntaxNode statements = RustTrees.child(block, RustKinds.STATEMENTS);
    if (statements == null) {
      return;
    }
    for (final SyntaxNode child : statements.children()) {
      if (RustKinds.STATEMENT.equals(child.kind())) {
        statement(child);
      } else {
        expression(child);
      }
    }
  }

  private void statement(final SyntaxNode statement) {
    final SyntaxNode inner = statement.children().get(0);
    switch (inner.kind()) {
      case "letStatement" -> lets.letStatement(inner);
      case "expressionStatement" -> expression(inner.children().get(0));
      case "macroInvocationSemi" -> flow.add(inner, false);
      case RustKinds.ITEM -> item(inner);
      default -> {
        // An empty statement.
      }
    }
  }

  private void item(final SyntaxNode item) {
    if (RustTrees.child(item, MACRO_ITEM) != null) {
      flow.add(item, false);
    }
  }

  private void expression(final SyntaxNode node) {
    if (RustKinds.WITH_BLOCK.equals(node.kind())) {
      withBlock(node);
      return;
    }
    final List<SyntaxNode> parts = node.children();
    if (parts.size() == 1 && RustKinds.WITH_BLOCK.equals(parts.get(0).kind())) {
      withBlock(parts.get(0));
    } else if (parts.isEmpty()) {
      flow.add(node, false);
    } else {
      jumps.jumpOrStep(node);
    }
  }

  private void withBlock(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final SyntaxNode last = parts.get(parts.size() - 1);
    switch (last.kind()) {
      case RustKinds.BLOCK -> branches.labeledOrPlain(node, last);
      case LOOP -> loops.loop(last);
      case "ifExpression" -> branches.ifExpression(last);
      case "matchExpression" -> branches.matchExpression(last);
      case "unsafeBlockExpression" -> block(RustTrees.child(last, RustKinds.BLOCK));
      case RustKinds.WITH_BLOCK -> withBlock(last);
      default -> flow.add(node, false);
    }
  }
}
