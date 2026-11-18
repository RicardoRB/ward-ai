package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.SyntaxNode;

/** Lowers {@code return}, {@code throw}, {@code break}, {@code continue} and {@code fallthrough}. */
final class SwiftJumpLowering {
  private final ControlFlowBuilder flow;

  /** Creates the lowering of the jumps of one body. */
  SwiftJumpLowering(final ControlFlowBuilder flow) {
    this.flow = flow;
  }

  void transfer(final SyntaxNode node) {
    switch (node.kind()) {
      case "return_statement" -> {
        SwiftFlowSyntax.step(flow, node);
        flow.tries().returnFrom();
      }
      case "throw_statement" -> {
        flow.add(node, true);
        flow.tries().throwOut();
      }
      case "break_statement" -> {
        flow.add(node, false);
        flow.jumps().breakTo(label(node));
      }
      case "continue_statement" -> {
        flow.add(node, false);
        flow.jumps().continueTo(label(node));
      }
      default -> flow.add(node, false);
    }
  }

  private static String label(final SyntaxNode node) {
    final SyntaxNode label = SwiftFlowSyntax.child(node, "label_name");
    return label == null ? null : SwiftFlowSyntax.text(label);
  }
}
