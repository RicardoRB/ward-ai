package dev.ward.analysis.domain.rust;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Lowers the {@code loop}, {@code while} and {@code for} expressions of one Rust body. */
final class RustLoopLowering {
  private final ControlFlowBuilder flow;
  private final Consumer<SyntaxNode> blocks;

  /**
   * Creates the lowering of the loops of one body.
   *
   * @param flow the builder of the body
   * @param blocks lowers the statements of a block of the body
   */
  RustLoopLowering(final ControlFlowBuilder flow, final Consumer<SyntaxNode> blocks) {
    this.flow = flow;
    this.blocks = blocks;
  }

  /** Lowers a {@code loopExpression}; its optional label names it for {@code break}. */
  void loop(final SyntaxNode node) {
    final SyntaxNode label = RustTrees.child(node, "loopLabel");
    final String name = label == null ? null : RustTrees.text(label).replace(":", "").strip();
    for (final SyntaxNode child : node.children()) {
      switch (child.kind()) {
        case "infiniteLoopExpression" -> infinite(child, name);
        case "predicateLoopExpression" -> whileLoop(child, name);
        case "iteratorLoopExpression" -> forLoop(child, name);
        default -> {
          // The label.
        }
      }
    }
  }

  private void infinite(final SyntaxNode node, final String label) {
    final int header = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    final SyntaxNode block = RustTrees.child(node, RustKinds.BLOCK);
    if (breaksInMacro(block, false)) {
      // The arms of a macro such as select! are tokens, so its break is not lowered as a jump.
      flow.edge(header, after, FlowEdgeKind.NORMAL);
    }
    body(block, label, after, header);
    flow.moveTo(after);
  }

  /** Whether a macro call below {@code node} has a {@code break} among its tokens. */
  private static boolean breaksInMacro(final SyntaxNode node, final boolean inMacro) {
    if (inMacro && RustTrees.isToken(node, "'break'")) {
      return true;
    }
    final boolean macro =
        inMacro || "macroInvocation".equals(node.kind()) || "macroInvocationSemi".equals(
            node.kind()
        );
    return node.children().stream().anyMatch(child -> breaksInMacro(child, macro));
  }

  private void whileLoop(final SyntaxNode node, final String label) {
    final int header = flow.newBlock();
    final int loopBody = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    flow.add(RustTrees.child(node, RustKinds.EXPRESSION), false);
    flow.branch(loopBody, after);
    flow.moveTo(loopBody);
    body(RustTrees.child(node, RustKinds.BLOCK), label, after, header);
    flow.moveTo(after);
  }

  /**
   * The pattern and the iterable are one step in the loop body, so the variable takes what the
   * iterable carries.
   */
  private void forLoop(final SyntaxNode node, final String label) {
    final SyntaxNode pattern = RustTrees.child(node, RustKinds.PATTERN);
    final SyntaxNode iterable = RustTrees.child(node, RustKinds.EXPRESSION);
    final int header = flow.newBlock();
    final int loopBody = flow.newBlock();
    final int after = flow.newBlock();
    flow.edge(flow.current(), header, FlowEdgeKind.NORMAL);
    flow.moveTo(header);
    flow.branch(loopBody, after);
    flow.moveTo(loopBody);
    flow.add(
        new SyntaxNode(
            "forHeader",
            RustTrees.join(pattern.span(), iterable.span()),
            Map.of(),
            List.of(pattern, iterable)
        ),
        false
    );
    body(RustTrees.child(node, RustKinds.BLOCK), label, after, header);
    flow.moveTo(after);
  }

  /** Lowers a loop body: {@code break} leaves to {@code after}, {@code continue} to the test. */
  private void body(final SyntaxNode block, final String label, final int after, final int test) {
    flow.jumps().pushLoop(label, after, test);
    blocks.accept(block);
    flow.jumps().popFrame();
    flow.edge(flow.current(), test, FlowEdgeKind.NORMAL);
  }
}
