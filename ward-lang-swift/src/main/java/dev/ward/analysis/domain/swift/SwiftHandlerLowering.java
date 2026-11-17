package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.ControlFlowBuilder;
import dev.ward.analysis.domain.ir.FlowEdgeKind;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Lowers the {@code switch} and {@code do}-{@code catch} statements of one Swift body. */
final class SwiftHandlerLowering {
  private static final String CODE_BLOCK = "code_block";
  private static final String PATTERN = "pattern";
  private static final String SWITCH_CASES = "switch_cases";

  private final ControlFlowBuilder flow;
  private final Consumer<SyntaxNode> statements;

  /**
   * Creates the lowering of the handlers of one body.
   *
   * @param flow the builder of the body
   * @param statements lowers the statements of a block of the body
   */
  SwiftHandlerLowering(final ControlFlowBuilder flow, final Consumer<SyntaxNode> statements) {
    this.flow = flow;
    this.statements = statements;
  }

  void switchStatement(final SyntaxNode node) {
    SwiftFlowSyntax.step(flow, SwiftFlowSyntax.child(node, "expression"));
    final int after = flow.newBlock();
    final int test = flow.current();
    final List<SyntaxNode> cases = new ArrayList<>();
    collect(node, "switch_case", SWITCH_CASES, cases);
    flow.jumps().pushSwitch(after);
    boolean exhaustive = false;
    for (final SyntaxNode branch : cases) {
      exhaustive |= "default_label".equals(branch.children().get(0).kind());
      final int entry = flow.newBlock();
      flow.edge(test, entry, FlowEdgeKind.TRUE);
      flow.moveTo(entry);
      SwiftFlowSyntax.step(flow, branch.children().get(0));
      statements.accept(SwiftFlowSyntax.child(branch, "statements"));
      flow.edge(flow.current(), after, FlowEdgeKind.NORMAL);
    }
    flow.jumps().popFrame();
    if (!exhaustive) {
      flow.edge(test, after, FlowEdgeKind.FALSE);
    }
    flow.moveTo(after);
  }

  void doStatement(final SyntaxNode node) {
    final List<SyntaxNode> catches = new ArrayList<>();
    collect(node, "catch_clause", "catch_clauses", catches);
    flow.tries().pushTry(!catches.isEmpty(), false);
    statements.accept(SwiftFlowSyntax.child(node, CODE_BLOCK));
    flow.tries().endTryBody();
    for (final SyntaxNode handler : catches) {
      flow.tries().beginCatch();
      final SyntaxNode pattern = SwiftFlowSyntax.child(handler, PATTERN);
      if (pattern != null) {
        flow.add(pattern, false);
      }
      statements.accept(SwiftFlowSyntax.child(handler, CODE_BLOCK));
      flow.tries().endCatch();
    }
    flow.tries().beginFinally();
    flow.tries().popTry();
  }

  /** The nodes of {@code kind} among the children of {@code node} and of its {@code list} nodes. */
  private static void collect(
      final SyntaxNode node,
      final String kind,
      final String list,
      final List<SyntaxNode> found
  ) {
    for (final SyntaxNode child : node.children()) {
      if (kind.equals(child.kind())) {
        found.add(child);
      } else if (list.equals(child.kind())) {
        collect(child, kind, list, found);
      }
    }
  }
}
